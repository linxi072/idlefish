package com.idlefish.trade.member.controller;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.member.entity.MemberLevel;
import com.idlefish.trade.member.service.MemberLevelService;
import com.idlefish.trade.member.vo.UserGrowthVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;

/**
 * 会员等级运营后台（F-13.2）：等级配置（阈值 + 权益）的查询与维护。
 * 等级配置持久化于 t_member_level，保存/删除后立即生效，无需重启。
 */
@RestController
@RequestMapping("/api/admin/member/level")
public class AdminMemberLevelController {

    private final MemberLevelService memberLevelService;

    public AdminMemberLevelController(MemberLevelService memberLevelService) {
        this.memberLevelService = memberLevelService;
    }

    /** 等级配置清单（按阈值升序）。 */
    @GetMapping("/list")
    public Result<List<MemberLevel>> list(@CurrentAdmin AdminUser admin) {
        return Result.ok(memberLevelService.listTiers());
    }

    /** 会员成长值概览（分页）。 */
    @GetMapping("/growth")
    public Result<IPage<UserGrowthVO>> growth(@CurrentAdmin AdminUser admin,
                                             @RequestParam(defaultValue = "1") Integer page,
                                             @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(memberLevelService.listGrowth(page, size));
    }

    /** 新增 / 更新等级配置（按 id 是否存在 upsert）。 */
    @PostMapping("/save")
    public Result<Long> save(@CurrentAdmin AdminUser admin, @RequestBody MemberLevel tier) {
        return Result.ok(memberLevelService.saveTier(tier));
    }

    /** 删除等级配置。 */
    @PostMapping("/delete")
    public Result<Void> delete(@CurrentAdmin AdminUser admin, @RequestParam Long id) {
        memberLevelService.deleteTier(id);
        return Result.ok();
    }
}
