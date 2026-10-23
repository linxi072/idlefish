package com.idlefish.trade.export;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.util.CryptoUtil;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.mapper.UserMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 用户导出（ITER-T06）：无 {@code org_id} 列，仅「不受限（ALL）」管理员可导出（REQ-07 防跨机构泄露）。
 * 手机号加密存储，导出前解密再做脱敏（138****8000），绝不落明文。
 */
@Component
public class UserExportProvider implements ExportProvider {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserMapper userMapper;
    private final CryptoUtil cryptoUtil;

    public UserExportProvider(UserMapper userMapper, CryptoUtil cryptoUtil) {
        this.userMapper = userMapper;
        this.cryptoUtil = cryptoUtil;
    }

    @Override
    public ExportType type() {
        return ExportType.USER;
    }

    @Override
    public boolean hasOrgScope() {
        return false;
    }

    @Override
    public List<String> headers() {
        return Arrays.asList("id", "wxOpenid", "phone", "nickname", "creditScore",
                "status", "realNameVerified", "createdAt");
    }

    @Override
public List<List<String>> rows(ExportContext ctx) {
        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<>();
        w.last("LIMIT " + ctx.getLimit());
        List<User> list = userMapper.selectList(w);
        List<List<String>> rows = new ArrayList<>(list.size());
        for (User u : list) {
            String phone = u.getPhone() == null ? null : cryptoUtil.decrypt(u.getPhone());
            rows.add(Arrays.asList(
                    str(u.getId()),
                    str(u.getWxOpenid()),
                    ExportCsvBuilder.maskPhone(phone),
                    str(u.getNickname()),
                    str(u.getCreditScore()),
                    str(u.getStatus()),
                    str(u.getRealNameVerified()),
                    fmt(u.getCreatedAt())));
        }
        return rows;
    }

    static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    static String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(FMT);
    }
}
