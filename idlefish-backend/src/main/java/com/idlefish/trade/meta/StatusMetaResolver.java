package com.idlefish.trade.meta;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.meta.vo.StatusGroupVO;
import com.idlefish.trade.meta.vo.StatusMetaVO;

import java.util.List;

/**
 * 状态元数据解析（ITER-U07）：纯函数，无 DB / 无 Spring 依赖，离线可单测。
 * <p>多端拿到的状态 code 统一经此处映射为「名称 + 语义色」，避免各端硬编码导致的色值/文案漂移。</p>
 */
public final class StatusMetaResolver {

    private StatusMetaResolver() {
    }

    /** 全部状态分组（订单/商品/支付/退款/维权/审核）。 */
    public static List<StatusGroupVO> allGroups() {
        return MetaDictionary.statusGroups();
    }

    /** 按业务域解析一组状态；域不存在抛 PARAM_INVALID。 */
    public static StatusGroupVO resolveDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            throw new BizException(Code.PARAM_INVALID, "domain 不能为空");
        }
        return MetaDictionary.statusGroups().stream()
                .filter(g -> g.domain().equals(domain))
                .findFirst()
                .orElseThrow(() -> new BizException(Code.PARAM_INVALID, "未知状态域: " + domain));
    }

    /** 按业务域 + 状态 code 解析单个状态元数据；不存在抛 PARAM_INVALID。 */
    public static StatusMetaVO resolveOne(String domain, String code) {
        StatusGroupVO group = resolveDomain(domain);
        if (code == null || code.isBlank()) {
            throw new BizException(Code.PARAM_INVALID, "code 不能为空");
        }
        return group.items().stream()
                .filter(m -> m.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new BizException(Code.PARAM_INVALID,
                        "未知状态: domain=" + domain + ", code=" + code));
    }
}
