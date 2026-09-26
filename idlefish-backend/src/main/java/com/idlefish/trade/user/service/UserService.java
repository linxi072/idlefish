package com.idlefish.trade.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.util.CryptoUtil;
import com.idlefish.trade.common.util.JwtUtil;
import com.idlefish.trade.user.dto.LoginResult;
import com.idlefish.trade.user.dto.TokenPair;
import com.idlefish.trade.user.dto.WxSession;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.mapper.UserMapper;
import com.idlefish.trade.user.vo.UserInfoVO;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户与鉴权服务：微信登录（code→openid，Mock/真实可切换）、令牌签发、手机号绑定（限频 + 加密）。
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final CryptoUtil cryptoUtil;
    private final JwtUtil jwtUtil;
    private final WechatLoginService wechatLoginService;

    /** 手机号绑定限频（单体单实例内存实现，生产用 Redis）：userId -> 上次绑定时间 */
    private final Map<Long, Long> bindLimit = new ConcurrentHashMap<>();

    public UserService(UserMapper userMapper, CryptoUtil cryptoUtil, JwtUtil jwtUtil,
                       WechatLoginService wechatLoginService) {
        this.userMapper = userMapper;
        this.cryptoUtil = cryptoUtil;
        this.jwtUtil = jwtUtil;
        this.wechatLoginService = wechatLoginService;
    }

    /** 微信登录：通过 WechatLoginService（Mock/真实可切换）将 js_code 换成 openid 并签发双令牌。 */
    public LoginResult login(String jsCode) {
        WxSession session = wechatLoginService.code2Session(jsCode);
        User user = loadOrCreateByOpenid(session.getOpenid());
        // 真实模式下回填 unionid（同一微信开放平台账号下跨端一致），仅首次写入避免覆盖
        if (session.getUnionid() != null
                && (user.getWxUnionid() == null || user.getWxUnionid().isBlank())) {
            User upd = new User();
            upd.setId(user.getId());
            upd.setWxUnionid(session.getUnionid());
            userMapper.updateById(upd);
            user.setWxUnionid(session.getUnionid());
        }
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

    /** 批量按 id 加载用户并组装为 Map（用于列表聚合，避免逐条查询产生的 N+1）。缺失 id 自然不出现在结果中。 */
    public Map<Long, User> mapByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Map<Long, User> m = new HashMap<>();
        for (User u : userMapper.selectBatchIds(ids)) m.put(u.getId(), u);
        return m;
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
