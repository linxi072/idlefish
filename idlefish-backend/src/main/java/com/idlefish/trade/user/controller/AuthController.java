package com.idlefish.trade.user.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.user.dto.LoginDTO;
import com.idlefish.trade.user.dto.LoginResult;
import com.idlefish.trade.user.dto.RefreshDTO;
import com.idlefish.trade.user.dto.TokenPair;
import com.idlefish.trade.user.service.UserService;
import com.idlefish.trade.user.vo.LoginVO;
import com.idlefish.trade.user.vo.TokenVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /** 微信登录（本地 Mock：以 code 作为 openid）。 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        LoginResult r = userService.login(dto.getCode());
        LoginVO vo = new LoginVO();
        vo.setToken(r.getToken());
        vo.setRefreshToken(r.getRefreshToken());
        vo.setUserInfo(userService.toInfo(r.getUser()));
        return Result.ok(vo);
    }

    /** 刷新令牌。 */
    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@Valid @RequestBody RefreshDTO dto) {
        TokenPair p = userService.refresh(dto.getRefreshToken());
        TokenVO vo = new TokenVO();
        vo.setToken(p.getToken());
        vo.setRefreshToken(p.getRefreshToken());
        return Result.ok(vo);
    }
}
