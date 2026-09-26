package com.idlefish.trade.marketing.invite.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.marketing.invite.entity.InviteRelation;
import com.idlefish.trade.marketing.invite.service.InviteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 邀请拉新接口（F-13.4）：获取/生成邀请码、绑定邀请关系、我的邀请列表。
 */
@RestController
@RequestMapping("/api/invite")
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    /** 获取（或生成）我的邀请码。 */
    @GetMapping("/code")
    public Result<String> myCode(@RequestParam Long userId) {
        return Result.ok(inviteService.ensureCode(userId));
    }

    /** 绑定邀请关系（注册/激活时调用）。 */
    @PostMapping("/bind")
    public Result<InviteRelation> bind(@RequestParam Long userId, @RequestParam String code) {
        return Result.ok(inviteService.bind(userId, code));
    }

    /** 我的邀请列表。 */
    @GetMapping("/invitees")
    public Result<List<InviteRelation>> invitees(@RequestParam Long userId) {
        return Result.ok(inviteService.myInvitees(userId));
    }
}
