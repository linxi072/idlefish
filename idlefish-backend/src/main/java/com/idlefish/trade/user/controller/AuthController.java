package com.idlefish.trade.user.controller;

import com.idlefish.trade.common.IdlefishProperties;
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
import jakarta.servlet.http.HttpServletResponse;
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
    private final IdlefishProperties props;

    /** 微信登录（本地 Mock：以 code 作为 openid）。 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletResponse response) {
        LoginResult r = userService.login(dto.getCode());
        LoginVO vo = new LoginVO();
        vo.setToken(r.getToken());
        vo.setRefreshToken(r.getRefreshToken());
        vo.setUserInfo(userService.toInfo(r.getUser()));
        // F-04 JWT→Cookie：浏览器端下发 HttpOnly + SameSite=Lax Cookie（移动端仍可用响应体令牌）
        setAuthCookies(response, r.getToken(), r.getRefreshToken());
        return Result.ok(vo);
    }

    /** 刷新令牌。 */
    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@Valid @RequestBody RefreshDTO dto, HttpServletResponse response) {
        TokenPair p = userService.refresh(dto.getRefreshToken());
        TokenVO vo = new TokenVO();
        vo.setToken(p.getToken());
        vo.setRefreshToken(p.getRefreshToken());
        setAuthCookies(response, p.getToken(), p.getRefreshToken());
        return Result.ok(vo);
    }

    /** 下发 access_token / refresh_token 两个 HttpOnly Cookie（防 XSS 窃取，SameSite=Lax 防 CSRF 基础面）。 */
    private void setAuthCookies(HttpServletResponse response, String access, String refresh) {
        boolean secure = props.getCookie().isSecure();
        response.addHeader("Set-Cookie", buildCookie("access_token", access, props.getJwt().getExpireSeconds(), secure));
        response.addHeader("Set-Cookie", buildCookie("refresh_token", refresh, props.getJwt().getRefreshSeconds(), secure));
    }

    private String buildCookie(String name, String value, long maxAgeSeconds, boolean secure) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('=').append(value)
                .append("; Path=/; HttpOnly; SameSite=Lax; Max-Age=").append(maxAgeSeconds);
        if (secure) {
            sb.append("; Secure");
        }
        return sb.toString();
    }
}
