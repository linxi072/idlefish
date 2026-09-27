package com.idlefish.trade.member.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.member.service.MemberLevelService;
import com.idlefish.trade.member.vo.MemberLevelView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员等级用户端接口（需登录）：查询自己的等级、权益与升级进度。
 */
@RestController
@RequestMapping("/api/member/level")
public class MemberLevelController {

    private final MemberLevelService memberLevelService;

    public MemberLevelController(MemberLevelService memberLevelService) {
        this.memberLevelService = memberLevelService;
    }

    /** 我的会员等级与权益 + 升级进度。 */
    @GetMapping("/mine")
    public Result<MemberLevelView> mine(@CurrentUser LoginUser user) {
        return Result.ok(memberLevelService.getLevel(user.getUserId()));
    }
}
