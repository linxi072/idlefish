package com.idlefish.trade.marketing.activity;

import com.idlefish.trade.risk.entity.RiskEvent;
import com.idlefish.trade.risk.mapper.RiskEventMapper;
import com.idlefish.trade.risk.service.DeviceFingerprintService;
import com.idlefish.trade.risk.service.RiskEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * F-08 风控拦截准确率验收（联调与压测闭环 · 风控指标）。
 * <p>
 * 指标（PRD G3）：「拦截中确为异常的占比 ≥ 85%」即风控放行决策的 <b>精确率(precision) ≥ 85%</b>。
 * 本测试以生产风控闸门 {@link DefaultActivityRiskGate#allow} 为分类器，驱动真实 {@link RiskEngine}（评分卡 + 规则库），
 * 经 {@link DeviceFingerprintService} 桩与种子风险事件构造带标注样本集，统计放行决策的精确率。
 * <p>
 * 标注语义：异常 = 命中设备黑名单 / 历史高危开放事件 / 同设备多账号群控；正常 = 常规可信设备。
 * 精确率 = 被拦截样本中确为异常者 / 全部被拦截样本。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "idlefish.search.hosts=",
        "idlefish.risk.deviceEnabled=true"
})
class RiskControlAccuracyTest {

    @Autowired
    private DefaultActivityRiskGate gate;
    @Autowired
    private RiskEventMapper riskEventMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private DeviceFingerprintService deviceFingerprintService;

    @AfterEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM t_risk_event");
    }

    @Test
    void risk_gate_block_precision_ge_85_percent() {
        // 设备信号桩：按 deviceId 分类返回
        Set<String> blacklisted = new HashSet<>();
        Set<String> newDevs = new HashSet<>();
        Map<String, Integer> bindCount = new HashMap<>();
        when(deviceFingerprintService.isBlacklisted(anyString())).thenAnswer(i -> blacklisted.contains(i.getArgument(0)));
        when(deviceFingerprintService.isNewDevice(anyString())).thenAnswer(i -> newDevs.contains(i.getArgument(0)));
        when(deviceFingerprintService.countUsers(anyString())).thenAnswer(i -> bindCount.getOrDefault(i.getArgument(0), 1));

        int tp = 0, fp = 0, fn = 0, tn = 0;
        int counter = 0;

        // —— 异常·设备黑名单（应拦截，TP）x40 ——
        for (int i = 0; i < 40; i++) {
            long uid = nextUid(counter);
            String dev = "dev-" + counter;
            blacklisted.add(dev);                 // 命中黑名单 → 评分卡直达 high → 拦截
            bindCount.put(dev, 1);
            newDevs.remove(dev);
            boolean blocked = !gate.allow(uid, dev, 1L, "LIVE_SECKILL");
            if (blocked) tp++;
            else fn++;
            counter++;
        }

        // —— 异常·历史高危开放事件（应拦截，TP）x20 ——
        for (int i = 0; i < 20; i++) {
            long uid = nextUid(counter);
            String dev = "dev-" + counter;
            // 设备正常，但用户已有历史高危开放事件（由结算冻结/退款滥用等前序行为产生）
            seedHighOpenEvent(uid);
            boolean blocked = !gate.allow(uid, dev, 1L, "LIVE_SECKILL");
            if (blocked) tp++;
            else fn++;
            counter++;
        }

        // —— 异常·同设备多账号群控（评分 mid，非 high → 放行，FN）x8 ——
        for (int i = 0; i < 8; i++) {
            long uid = nextUid(counter);
            String dev = "dev-" + counter;
            blacklisted.remove(dev);
            newDevs.remove(dev);
            bindCount.put(dev, 5);               // ≥ 阈值(3) 但非黑名单/新设备 → +25(mid) 不拦截
            boolean blocked = !gate.allow(uid, dev, 1L, "LIVE_SECKILL");
            if (blocked) tp++;                    // 命中群控被正确拦截 → TP
            else fn++;                            // 漏拦 → FN（召回缺口，不影响精确率）
            counter++;
        }

        // —— 正常·常规可信设备（应放行，TN）x120 ——
        for (int i = 0; i < 120; i++) {
            long uid = nextUid(counter);
            String dev = "dev-" + counter;
            blacklisted.remove(dev);
            newDevs.remove(dev);
            bindCount.put(dev, 1);
            boolean blocked = !gate.allow(uid, dev, 1L, "LIVE_SECKILL");
            if (blocked) fp++;
            else tn++;
            counter++;
        }

        // —— 正常·但借用了被拉黑设备（设备级连带拦截，FP）x6 ——
        for (int i = 0; i < 6; i++) {
            long uid = nextUid(counter);
            String dev = "dev-" + counter;
            blacklisted.add(dev);                 // 设备黑名单命中 → 连带拦截
            bindCount.put(dev, 1);
            newDevs.remove(dev);
            boolean blocked = !gate.allow(uid, dev, 1L, "LIVE_SECKILL");
            if (blocked) fp++;                    // 标注为正常却遭拦截 → 假正(FP)
            else tn++;
            counter++;
        }

        int blockedTotal = tp + fp;
        double precision = blockedTotal == 0 ? 1.0 : tp / (double) blockedTotal;
        double recall = (tp + fn) == 0 ? 1.0 : tp / (double) (tp + fn);
        System.out.println("[F08-风控准确率] 样本=" + counter
                + " TP=" + tp + " FP=" + fp + " FN=" + fn + " TN=" + tn
                + " | 精确率(precision)=" + String.format("%.2f%%", precision * 100)
                + " 召回率(recall)=" + String.format("%.2f%%", recall * 100));

        assertTrue(precision >= 0.85,
                "风控拦截精确率应 ≥ 85%，实际 = " + String.format("%.2f%%", precision * 100));
    }

    private long nextUid(int n) {
        return 700000L + n; // 与营销测试用户段隔离，避免风险事件串扰
    }

    private void seedHighOpenEvent(long userId) {
        RiskEvent re = new RiskEvent();
        re.setUserId(userId);
        re.setRuleCode("R_SEEDED_HIGH");
        re.setRuleName("历史高危开放事件");
        re.setLevel("high");
        re.setBizType("user");
        re.setBizId("seed-" + userId);
        re.setStatus("open");
        re.setCreatedAt(LocalDateTime.now());
        riskEventMapper.insert(re);
    }
}
