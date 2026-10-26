package com.idlefish.trade.marketing.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.marketing.activity.BargainConfig;
import com.idlefish.trade.marketing.activity.LotteryPrize;
import com.idlefish.trade.marketing.activity.entity.ActivityRecord;
import com.idlefish.trade.marketing.activity.entity.LotteryPrizeEntity;
import com.idlefish.trade.marketing.activity.entity.MarketingActivity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.marketing.activity.mapper.ActivityRecordMapper;
import com.idlefish.trade.marketing.activity.mapper.ActivityReconciliationMapper;
import com.idlefish.trade.marketing.activity.mapper.LotteryPrizeMapper;
import com.idlefish.trade.marketing.activity.mapper.MarketingActivityMapper;
import com.idlefish.trade.marketing.activity.service.ActivityKernelService;
import com.idlefish.trade.marketing.activity.service.LiveSeckillService;
import com.idlefish.trade.marketing.activity.service.LotteryService;
import com.idlefish.trade.marketing.activity.service.MktBargainService;
import com.idlefish.trade.marketing.activity.vo.ActivityJoinResultVO;
import com.idlefish.trade.marketing.activity.vo.BargainSessionVO;
import com.idlefish.trade.risk.service.RiskEngine;
import com.idlefish.trade.marketing.service.CouponService;
import com.idlefish.trade.marketing.activity.SeckillSettleCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-08 营销玩法联调集成测试（联调与压测闭环 · 正确性部分）。
 * <p>
 * 真实连库（MySQL）+ 真实 Redis，覆盖三玩法核心链路：
 * ① 直播秒杀强一致不超卖（DB 路径）② 幂等 ③ Redis 预扣不超卖（G3 性能路径）+ 异步结算最终一致
 * ④ 砍价完整流成团 ⑤ 抽奖防超发 ⑥ 对账一致 ⑦ 本地吞吐探针（仅指示性）。
 * 风控经 {@link RiskEngine} 桩置为放行，隔离营销链路正确性；风控准确率见 {@code RiskControlAccuracyTest}。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "idlefish.search.hosts=",                 // 关闭 ES，避免优雅降级噪声
        "idlefish.risk.deviceEnabled=true"
})
class MarketingIntegrationTest {

    @Autowired
    private LiveSeckillService liveSeckillService;
    @Autowired
    private ActivityKernelService kernel;
    @Autowired
    private MktBargainService bargainService;
    @Autowired
    private LotteryService lotteryService;
    @Autowired
    private MarketingActivityMapper activityMapper;
    @Autowired
    private ActivityRecordMapper recordMapper;
    @Autowired
    private LotteryPrizeMapper prizeMapper;
    @Autowired
    private ActivityReconciliationMapper reconMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    /** 风控放行，隔离营销正确性。 */
    @MockBean
    private RiskEngine riskEngine;
    /** 抽奖发券回落，避免依赖券模板。 */
    @MockBean
    private CouponService couponService;

    private static final String STOCK_KEY_PREFIX = "act:stock:";

    @BeforeEach
    @AfterEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM t_activity_record");
        jdbcTemplate.execute("DELETE FROM t_activity_reconciliation");
        jdbcTemplate.execute("DELETE FROM t_marketing_activity");
        jdbcTemplate.execute("DELETE FROM t_bargain_session");
        jdbcTemplate.execute("DELETE FROM t_bargain_assist");
        jdbcTemplate.execute("DELETE FROM t_lottery_prize");
        jdbcTemplate.execute("DELETE FROM t_seckill_settle_task");
        jdbcTemplate.execute("DELETE FROM t_risk_event");
        // 清理预扣缓存
        for (String k : redisTemplate.keys(STOCK_KEY_PREFIX + "*")) {
            redisTemplate.delete(k);
        }
    }

    // ============ ① 直播秒杀强一致不超卖（DB 路径，未预热） ============

    @Test
    void seckill_strongConsistent_noOversell() throws InterruptedException {
        int stock = 100;
        long actId = insertActivity("LIVE_SECKILL", stock, 1000, "ONGOING");
        // 不调用 publish → 不预热缓存 → grab 走 DB 强一致原子扣减路径

        int threads = 200;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger settled = new AtomicInteger();
        AtomicInteger preempted = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            final long uid = i + 1;
            pool.submit(() -> {
                try {
                    start.await();
                    liveSeckillService.grab(uid, "dev-" + uid, actId, "req-" + uid);
                    settled.incrementAndGet();
                } catch (BizException e) {
                    if (e.getCode() == Code.STOCK_NOT_ENOUGH.getCode()) {
                        preempted.incrementAndGet();
                    } else {
                        throw e;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "秒杀并发未在 60s 内完成");

        // 不超卖：恰好 stock 个成功落库，其余被库存网关拒绝
        assertEquals(stock, settled.get(), "成功落库数应等于库存，证明不超卖");
        assertEquals(threads - stock, preempted.get(), "剩余请求应被库存网关拒绝");
        MarketingActivity after = activityMapper.selectById(actId);
        assertEquals(0, after.getStock(), "DB 库存不应为负，终值应为 0");
        assertEquals(stock, after.getSoldCount(), "已售计数应等于库存");
        assertEquals(stock, recordMapper.selectCount(null), "参与记录数应等于库存");
    }

    // ============ ② 幂等：同 requestId 重复抢购仅生效一次 ============

    @Test
    void seckill_idempotent_sameRequestId() {
        long actId = insertActivity("LIVE_SECKILL", 1000, 1000, "ONGOING");
        String requestId = "r-immutable";
        ActivityJoinResultVO first = liveSeckillService.grab(1001L, "dev-1001", actId, requestId);
        ActivityJoinResultVO second = liveSeckillService.grab(1001L, "dev-1001", actId, requestId);
        assertNotNull(first.getRecordNo());
        assertEquals(first.getRecordNo(), second.getRecordNo(), "同一 requestId 应返回既有记录（幂等）");
        assertEquals(1, recordMapper.selectCount(null), "重复幂等键不应产生第二条记录");
        MarketingActivity after = activityMapper.selectById(actId);
        assertEquals(999, after.getStock(), "幂等仅扣减一次库存");
    }

    // ============ ③ Redis 预扣不超卖（G3 性能路径）+ 异步结算最终一致 ============

    @Test
    void seckill_redisPreDeduct_noOversell_and_asyncSettle_consistent() throws InterruptedException {
        int stock = 100;
        long actId = insertActivity("LIVE_SECKILL", stock, 1000, "DRAFT");
        kernel.publish(actId); // 发布（DRAFT→ONGOING）并预热缓存 stock=100

        int threads = 200;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger preempted = new AtomicInteger();
        List<SeckillSettleCommand> cmds = new ArrayList<>();
        Object lock = new Object();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            final long uid = i + 1;
            final String req = "req-" + uid;
            pool.submit(() -> {
                try {
                    start.await();
                    ActivityJoinResultVO vo = liveSeckillService.grab(uid, "dev-" + uid, actId, req);
                    if ("ACCEPTED".equals(vo.getStatus())) {
                        accepted.incrementAndGet();
                        SeckillSettleCommand cmd = new SeckillSettleCommand();
                        cmd.setIdempotencyKey("seckill:" + uid + ":" + actId + ":" + req);
                        cmd.setUserId(uid);
                        cmd.setActivityId(actId);
                        cmd.setDeviceId("dev-" + uid);
                        cmd.setBizId("seckill:" + actId + ":" + uid);
                        synchronized (lock) {
                            cmds.add(cmd);
                        }
                    } else {
                        preempted.incrementAndGet();
                    }
                } catch (BizException e) {
                    if (e.getCode() == Code.LIVE_SECKILL_PREEMPTED.getCode()) {
                        preempted.incrementAndGet();
                    } else {
                        throw e;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "预扣并发未在 60s 内完成");

        // 缓存预扣层不超卖：恰好 stock 个预扣成功，其余快速拒绝
        assertEquals(stock, accepted.get(), "Redis 预扣成功数应等于库存");
        assertEquals(threads - stock, preempted.get(), "其余请求应被预扣拒绝");
        Object remain = redisTemplate.opsForValue().get(STOCK_KEY_PREFIX + actId);
        assertNotNull(remain);
        assertTrue(((Number) remain).longValue() >= 0, "缓存库存不应为负");

        // 异步结算最终一致：逐条 settle（幂等），DB 库存归零、已售=stock
        for (SeckillSettleCommand cmd : cmds) {
            liveSeckillService.settle(cmd);
        }
        MarketingActivity after = activityMapper.selectById(actId);
        assertEquals(0, after.getStock(), "结算后 DB 库存应为 0");
        assertEquals(stock, after.getSoldCount(), "结算后已售应等于库存");

        // 重放幂等：同一指令再次 settle 不重复扣减/不新增记录
        int before = recordMapper.selectCount(null).intValue();
        for (SeckillSettleCommand cmd : cmds) {
            liveSeckillService.settle(cmd);
        }
        assertEquals(before, recordMapper.selectCount(null).intValue(), "重放指令不应产生重复记录");
    }

    // ============ ④ 砍价完整流：助力至成团 ============

    @Test
    void bargain_fullFlow_qualify() {
        long actId = insertBargainActivity();
        BargainSessionVO session = bargainService.createSession(2001L, "dev-2001", actId);
        assertEquals("ONGOING", session.getStatus());
        int target = session.getTargetAssistCount();
        for (int i = 0; i < target; i++) {
            long assistant = 9000L + i; // 不同助力者
            bargainService.assist(session.getSessionNo(), assistant, "a-dev-" + assistant);
        }
        BargainSessionVO finalSession = bargainService.getSession(session.getSessionNo());
        assertEquals("QUALIFIED", finalSession.getStatus(), "达到目标助力数应成团");
        assertTrue(finalSession.getCurrentPriceFen() >= session.getFloorPriceFen(), "成团价不应低于底价");
    }

    // ============ ⑤ 抽奖防超发：奖品库存原子扣减不越界 ============

    @Test
    void lottery_noPrizeOversell() {
        long actId = insertActivity("LOTTERY", 5000, 0, "ONGOING"); // 名额充足，隔离奖品库存维度
        List<LotteryPrize> prizes = new ArrayList<>();
        prizes.add(prize("P1", "COUPON", "coupon", 100L, new BigDecimal("0.10"), 10, 1L));
        prizes.add(prize("P2", "PHYSICAL", "gift", 50000L, new BigDecimal("0.05"), 5, null));
        lotteryService.savePrizes(actId, prizes);

        int draws = 1000;
        for (int i = 0; i < draws; i++) {
            lotteryService.draw(3000L + i, "dev-" + (3000L + i), actId);
        }

        // 每个奖品的实际扣减 = 初始库存 - 剩余库存，且剩余库存不为负、扣减不超初始库存
        for (LotteryPrize p : prizes) {
            LotteryPrizeEntity entity = prizeMapper.selectOne(new LambdaQueryWrapper<LotteryPrizeEntity>()
                    .eq(LotteryPrizeEntity::getActivityId, actId)
                    .eq(LotteryPrizeEntity::getPrizeId, p.getPrizeId()));
            int remain = entity.getStock();
            assertTrue(remain >= 0, "奖品剩余库存不应为负");
            int consumed = initialStock(p.getPrizeId()) - remain;
            assertTrue(consumed >= 0 && consumed <= initialStock(p.getPrizeId()),
                    "奖品消耗不应超出发放份数：" + p.getPrizeId());
        }
        // 概率配置合法（总和 ≤1）
        assertTrue(LotteryDrawer.validateProbabilities(prizes), "抽奖概率总和应 ≤1");
    }

    private final ConcurrentHashMap<String, Integer> prizeStock = new ConcurrentHashMap<>();

    private int initialStock(String prizeId) {
        return prizeStock.getOrDefault(prizeId, 0);
    }

    // ============ ⑥ 对账一致：stockSold + recycled == 初始库存 ============

    @Test
    void reconcile_consistency() {
        int stock = 60;
        long actId = insertActivity("LIVE_SECKILL", stock, 1000, "ONGOING");
        // 同步落库 40 笔（DB 路径，不预热）
        for (int i = 0; i < 40; i++) {
            liveSeckillService.grab(4000L + i, "dev-" + (4000L + i), actId, "req-" + i);
        }
        var recon = kernel.reconcile(actId);
        assertEquals(40, recon.getStockSold(), "已售应等于实际售出");
        // 对账总库存 = 对账时剩余库存（已售扣减后）；已售 + 回收 == 初始总库存
        assertEquals(stock, recon.getStockSold() + recon.getStockRecycled(), "已售 + 回收 应等于总库存");
        MarketingActivity after = activityMapper.selectById(actId);
        assertEquals("ENDED", after.getStatus(), "对账后活动应结束");
    }

    // ============ ⑦ 本地吞吐探针（仅指示性，非压测环境结论） ============

    @Test
    void local_throughput_probe_indicative() {
        long actId = insertActivity("LIVE_SECKILL", 100000, 100000, "ONGOING"); // 大库存避免耗尽
        int iterations = 2000;
        long t0 = System.nanoTime();
        int ok = 0;
        for (int i = 0; i < iterations; i++) {
            try {
                liveSeckillService.grab(5000L + i, "dev-" + (5000L + i), actId, "req-" + i);
                ok++;
            } catch (BizException ignored) {
                // 库存耗尽等，不计入吞吐
            }
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        double qps = elapsedMs > 0 ? (ok * 1000.0 / elapsedMs) : 0;
        System.out.println("[F08-吞吐探针] 单线程同步 grab " + ok + " 笔 / " + elapsedMs
                + "ms ≈ " + String.format("%.1f", qps) + " QPS（沙箱指示性，非 G3 压测结论）");
        assertTrue(ok > 0 && qps > 0, "吞吐探针应成功执行并产出正吞吐");
    }

    // ===================== 辅助 =====================

    private LotteryPrize prize(String prizeId, String type, String name, long valueFen,
                               BigDecimal prob, int stock, Long couponTemplateId) {
        prizeStock.put(prizeId, stock);
        LotteryPrize p = new LotteryPrize();
        p.setPrizeId(prizeId);
        p.setName(name);
        p.setType(type);
        p.setValueFen(valueFen);
        p.setProbability(prob);
        p.setStock(stock);
        p.setCouponTemplateId(couponTemplateId);
        return p;
    }

    private long insertActivity(String kind, int stock, int limitPerUser, String status) {
        MarketingActivity a = new MarketingActivity();
        a.setKind(kind);
        a.setTitle("IT-" + kind + "-" + System.nanoTime());
        a.setStatus(status);
        a.setStock(stock);
        a.setSoldCount(0);
        a.setLimitPerUser(limitPerUser);
        a.setAudienceType("ALL");
        a.setRiskLevel("LOW");
        a.setActivityPrice(10000L);
        a.setStartAt(LocalDateTime.now().minusHours(1));
        a.setEndAt(LocalDateTime.now().plusHours(1));
        a.setLossFloorFen(0L);
        activityMapper.insert(a);
        return a.getId();
    }

    private long insertBargainActivity() {
        BargainConfig cfg = new BargainConfig();
        cfg.setOriginPriceFen(100000L);   // 1000 元
        cfg.setFloorPriceFen(10000L);     // 100 元
        cfg.setMinCutFen(1000L);
        cfg.setMaxCutFen(5000L);
        cfg.setTargetAssistCount(5);
        cfg.setPerAssistRandom(false);
        MarketingActivity a = new MarketingActivity();
        a.setKind("BARGAIN");
        a.setTitle("IT-BARGAIN-" + System.nanoTime());
        a.setStatus("ONGOING");
        a.setStock(1000);
        a.setSoldCount(0);
        a.setLimitPerUser(1000);
        a.setAudienceType("ALL");
        a.setRiskLevel("LOW");
        a.setActivityPrice(100000L);
        a.setStartAt(LocalDateTime.now().minusHours(1));
        a.setEndAt(LocalDateTime.now().plusHours(1));
        a.setLossFloorFen(0L);
        try {
            a.setPlayConfig(objectMapper.writeValueAsString(cfg));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        activityMapper.insert(a);
        return a.getId();
    }
}
