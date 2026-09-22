package com.idlefish.trade.user.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.user.dto.BindPhoneDTO;
import com.idlefish.trade.user.service.UserService;
import com.idlefish.trade.user.vo.UserInfoVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/info")
    public Result<UserInfoVO> info(@CurrentUser LoginUser user) {
        return Result.ok(userService.toInfo(userService.getById(user.getUserId())));
    }

    /** 绑定手机号：60s 限频 + 加密存储。 */
    @PostMapping("/bind-phone")
    public Result<Void> bindPhone(@CurrentUser LoginUser user, @Valid @RequestBody BindPhoneDTO dto) {
        userService.bindPhone(user.getUserId(), dto.getPhone());
        return Result.ok();
    }
}
