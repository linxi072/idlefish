package com.idlefish.trade.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.util.CryptoUtil;
import com.idlefish.trade.common.util.JwtUtil;
import com.idlefish.trade.user.dto.LoginResult;
import com.idlefish.trade.user.dto.TokenPair;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.mapper.UserMapper;
import com.idlefish.trade.user.vo.UserInfoVO;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户与鉴权服务：微信登录（Mock 以 code 当 openid）、令牌签发、手机号绑定（限频 + 加密）。
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final CryptoUtil cryptoUtil;
    private final JwtUtil jwtUtil;

    /** 手机号绑定限频（单体单实例内存实现，生产用 Redis）：userId -> 上次绑定时间 */
    private final Map<Long, Long> bindLimit = new ConcurrentHashMap<>();

    public UserService(UserMapper userMapper, CryptoUtil cryptoUtil, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.cryptoUtil = cryptoUtil;
        this.jwtUtil = jwtUtil;
    }

    /** 微信登录：本地 Mock 直接以 code 作为 openid；生产应 code→微信换 openid。 */
    public LoginResult login(String code) {
        String openid = code;
        User user = loadOrCreateByOpenid(openid);
        TokenPair pair = issueToken(user);
        LoginResult result = new LoginResult();
        result.setUser(user);
        result.setToken(pair.getToken());
        result.setRefreshToken(pair.getRefreshToken());
        return result;
    }

    public User loadOrCreateByOpenid(String openid) {
        User exist = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getWxOpenid, openid));
        if (exist != null) {
            return exist;
        }
        User user = new User();
        user.setWxOpenid(openid);
        user.setNickname("用户" + openid.substring(Math.max(0, openid.length() - 6)));
        user.setCreditScore(100);
        user.setStatus(0);
        userMapper.insert(user);
        return user;
    }

    public TokenPair issueToken(User user) {
        String token = jwtUtil.generate(user.getId(), user.getWxOpenid());
        String refresh = jwtUtil.generateRefresh(user.getId(), user.getWxOpenid());
        return new TokenPair(token, refresh);
    }

    public User getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(Code.USER_NOT_FOUND);
        }
        return user;
    }

    /** 刷新 access token（校验 refresh token 后重新签发一对令牌）。 */
    public TokenPair refresh(String refreshToken) {
        io.jsonwebtoken.Claims claims = jwtUtil.parse(refreshToken);
        if (!Boolean.TRUE.equals(claims.get("refresh", Boolean.class))) {
            throw new BizException(Code.TOKEN_EXPIRED);
        }
        User user = getById(jwtUtil.getUserId(claims));
        return issueToken(user);
    }

    /** 绑定手机号：60s 限频 + 加密存储。 */
    public void bindPhone(Long userId, String phone) {
        long now = System.currentTimeMillis();
        Long last = bindLimit.get(userId);
        if (last != null && now - last < 60_000L) {
            throw new BizException(Code.FREQUENCY_LIMIT);
        }
        bindLimit.put(userId, now);
        User update = new User();
        update.setId(userId);
        update.setPhone(cryptoUtil.encrypt(phone));
        userMapper.updateById(update);
    }

    public UserInfoVO toInfo(User user) {
        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setCreditScore(user.getCreditScore());
        return vo;
    }
}
