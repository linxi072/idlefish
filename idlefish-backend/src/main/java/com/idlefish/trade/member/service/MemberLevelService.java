package com.idlefish.trade.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.idlefish.trade.member.MemberLevelCalculator;
import com.idlefish.trade.member.entity.MemberLevel;
import com.idlefish.trade.member.entity.UserGrowth;
import com.idlefish.trade.member.mapper.MemberLevelMapper;
import com.idlefish.trade.member.mapper.UserGrowthMapper;
import com.idlefish.trade.member.vo.MemberLevelView;
import com.idlefish.trade.member.vo.UserGrowthVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会员等级服务（F-13.2）：等级解析、成长值累计（交易/评价）、运营后台等级配置 CRUD。
 * 与 {@code CreditService}（F-06）解耦：会员权益仅消费等级结果，不直接耦合信用分。
 */
@Service
public class MemberLevelService {

    private final MemberLevelMapper memberLevelMapper;
    private final UserGrowthMapper userGrowthMapper;

    public MemberLevelService(MemberLevelMapper memberLevelMapper, UserGrowthMapper userGrowthMapper) {
        this.memberLevelMapper = memberLevelMapper;
        this.userGrowthMapper = userGrowthMapper;
    }

    /**
     * 启动种子：t_member_level 为空时写入默认 5 级，保证等级体系开箱可用。
     */
    @PostConstruct
    public void seedIfEmpty() {
        try {
            if (memberLevelMapper.selectCount(null) == 0) {
                for (MemberLevel t : defaultTiers()) {
                    memberLevelMapper.insert(t);
                }
            }
        } catch (Exception ignore) {
            // 无 DB 环境（如离线单测）跳过；运行时由运维初始化
        }
    }

    /** 当前用户等级视图（含权益与升级进度）。 */
    public MemberLevelView getLevel(Long userId) {
        List<MemberLevel> tiers = loadTiers();
        UserGrowth g = ensure(userId);
        int growth = g.getGrowthValue() == null ? 0 : g.getGrowthValue();
        MemberLevelCalculator.Resolved r = MemberLevelCalculator.resolve(tiers, growth);

        MemberLevelView v = new MemberLevelView();
        v.setLevelCode(g.getLevelCode());
        v.setGrowthValue(growth);
        if (r.current != null) {
            v.setLevelName(r.current.getLevelName());
            v.setIcon(r.current.getIcon());
            v.setColor(r.current.getColor());
            v.setFreeShipping(r.current.getFreeShipping());
            v.setPriorityReview(r.current.getPriorityReview());
            v.setCommissionDiscount(r.current.getCommissionDiscount());
        }
        v.setNextLevelName(r.next == null ? null : r.next.getLevelName());
        v.setGrowthToNext(r.growthToNext);
        v.setPercent(r.percent);
        return v;
    }

    /**
     * 增加成长值并实时重算等级（best-effort，不抛异常阻断调用方主流程）。
     * 在 PayService.onOrderPaid / ReviewService.approve 中以守卫方式调用。
     */
    public void addGrowth(Long userId, int delta) {
        if (delta <= 0) {
            return;
        }
        try {
            int n = userGrowthMapper.update(null, new UpdateWrapper<UserGrowth>()
                    .eq("user_id", userId)
                    .setSql("growth_value = growth_value + " + delta));
            if (n == 0) {
                UserGrowth g = new UserGrowth();
                g.setUserId(userId);
                g.setGrowthValue(delta);
                g.setLevelCode("L1");
                userGrowthMapper.insert(g);
            }
            UserGrowth g = userGrowthMapper.selectOne(
                    new LambdaQueryWrapper<UserGrowth>().eq(UserGrowth::getUserId, userId));
            if (g == null) {
                return;
            }
            MemberLevelCalculator.Resolved r = MemberLevelCalculator.resolve(loadTiers(),
                    g.getGrowthValue() == null ? 0 : g.getGrowthValue());
            String code = r.current == null ? "L1" : r.current.getLevelCode();
            if (!code.equals(g.getLevelCode())) {
                UserGrowth upd = new UserGrowth();
                upd.setId(g.getId());
                upd.setLevelCode(code);
                userGrowthMapper.updateById(upd);
            }
        } catch (Exception ignore) {
            // 成长值累计失败不影响交易/评价主流程
        }
    }

    // ===== 运营后台：等级配置 CRUD =====

    public List<MemberLevel> listTiers() {
        return loadTiers();
    }

    @Transactional
    public Long saveTier(MemberLevel tier) {
        if (tier.getId() == null) {
            memberLevelMapper.insert(tier);
        } else {
            memberLevelMapper.updateById(tier);
        }
        return tier.getId();
    }

    @Transactional
    public void deleteTier(Long id) {
        memberLevelMapper.deleteById(id);
    }

    /** 会员成长值概览（分页）：派生等级名称/图标/颜色。 */
    public IPage<UserGrowthVO> listGrowth(int page, int size) {
        Page<UserGrowth> pg = userGrowthMapper.selectPage(new Page<>(page, size), null);
        List<MemberLevel> tiers = loadTiers();
        Map<String, MemberLevel> byCode = tiers.stream()
                .collect(Collectors.toMap(MemberLevel::getLevelCode, t -> t, (a, b) -> a));
        List<UserGrowthVO> list = pg.getRecords().stream().map(g -> {
            UserGrowthVO v = new UserGrowthVO();
            v.setUserId(g.getUserId());
            v.setGrowthValue(g.getGrowthValue());
            v.setLevelCode(g.getLevelCode());
            MemberLevel t = byCode.get(g.getLevelCode());
            if (t != null) {
                v.setLevelName(t.getLevelName());
                v.setIcon(t.getIcon());
                v.setColor(t.getColor());
            }
            return v;
        }).toList();
        IPage<UserGrowthVO> result = new Page<>(pg.getCurrent(), pg.getSize(), pg.getTotal());
        result.setRecords(list);
        return result;
    }

    // ===== 内部 =====

    private List<MemberLevel> loadTiers() {
        List<MemberLevel> all = memberLevelMapper.selectList(null);
        all.sort(Comparator.comparingInt(t -> t.getMinGrowth() == null ? 0 : t.getMinGrowth()));
        return all;
    }

    private UserGrowth ensure(Long userId) {
        UserGrowth g = userGrowthMapper.selectOne(
                new LambdaQueryWrapper<UserGrowth>().eq(UserGrowth::getUserId, userId));
        if (g == null) {
            g = new UserGrowth();
            g.setUserId(userId);
            g.setGrowthValue(0);
            g.setLevelCode("L1");
            userGrowthMapper.insert(g);
        }
        return g;
    }

    private List<MemberLevel> defaultTiers() {
        return List.of(
                tier("L1", "注册会员", 0, 0, 0, 0, "🥉", "#9e9e9e", 1),
                tier("L2", "青铜会员", 100, 0, 0, 0, "🥈", "#cd7f32", 2),
                tier("L3", "白银会员", 500, 1, 0, 0, "🥈", "#c0c0c0", 3),
                tier("L4", "黄金会员", 2000, 1, 1, 100, "🥇", "#ffd700", 4),
                tier("L5", "钻石会员", 8000, 1, 1, 200, "💎", "#b9f2ff", 5)
        );
    }

    private MemberLevel tier(String code, String name, int min, int fs, int pr, int cd,
                             String icon, String color, int sort) {
        MemberLevel t = new MemberLevel();
        t.setLevelCode(code);
        t.setLevelName(name);
        t.setMinGrowth(min);
        t.setFreeShipping(fs);
        t.setPriorityReview(pr);
        t.setCommissionDiscount(cd);
        t.setIcon(icon);
        t.setColor(color);
        t.setSortOrder(sort);
        return t;
    }
}
