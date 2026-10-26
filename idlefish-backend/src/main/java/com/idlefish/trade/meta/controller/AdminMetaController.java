package com.idlefish.trade.meta.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.meta.MetaDictionary;
import com.idlefish.trade.meta.StatusMetaResolver;
import com.idlefish.trade.meta.vo.DesignTokenVO;
import com.idlefish.trade.meta.vo.StatusGroupVO;
import com.idlefish.trade.meta.vo.TermGlossaryVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 多端体验对齐（ITER-U07）元数据接口：状态枚举字典 / 业务术语表 / 设计令牌映射。
 * <p>只读参考数据源，由 {@link MetaDictionary} 单一维护，供小程序 / pc-admin / H5 引用，
 * 保证状态文案、语义色、设计令牌三端一致。端点位于 /api/admin/meta，受后台鉴权拦截器保护。</p>
 */
@RestController
@RequestMapping("/api/admin/meta")
public class AdminMetaController {

    /** 全部状态枚举字典（按订单/商品/支付/退款/维权/审核分组）。 */
    @GetMapping("/status-dict")
    public Result<List<StatusGroupVO>> statusDict() {
        return Result.ok(StatusMetaResolver.allGroups());
    }

    /** 单个业务域的状态枚举字典（如 order / item / dispute / audit）。 */
    @GetMapping("/status-dict/{domain}")
    public Result<StatusGroupVO> statusDictByDomain(@PathVariable String domain) {
        return Result.ok(StatusMetaResolver.resolveDomain(domain));
    }

    /** 业务术语表（跨端统一命名）。 */
    @GetMapping("/terms")
    public Result<List<TermGlossaryVO>> terms() {
        return Result.ok(MetaDictionary.terms());
    }

    /** 设计令牌映射（品牌/语义色/间距/字号，含多端落地）。 */
    @GetMapping("/design-tokens")
    public Result<List<DesignTokenVO>> designTokens() {
        return Result.ok(MetaDictionary.designTokens());
    }
}
