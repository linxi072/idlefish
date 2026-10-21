package com.idlefish.trade.pricing.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.pricing.dto.PriceDeviationRequest;
import com.idlefish.trade.pricing.dto.PriceEstimateRequest;
import com.idlefish.trade.pricing.service.PricingService;
import com.idlefish.trade.pricing.vo.PriceDeviationVO;
import com.idlefish.trade.pricing.vo.PriceEstimateVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 估价用户端接口（ITER-F03，需登录）：估价区间 / 偏离判定。
 *
 * <p>REQ-06 降级：服务无数据时 {@link PriceEstimateVO#isHasData()} 为 false，前端隐藏入口，不阻塞发布/议价。
 */
@RestController
@RequestMapping("/api/price")
public class PriceController {

    private final PricingService pricingService;

    public PriceController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    /** REQ-02 估价：选定品类+成色返回建议区间与样本数。 */
    @PostMapping("/estimate")
    public Result<PriceEstimateVO> estimate(@CurrentUser LoginUser user,
                                           @RequestBody PriceEstimateRequest req) {
        return Result.ok(pricingService.estimate(req.getCategoryId(), req.getConditionLevel()));
    }

    /** REQ-09 偏离判定：给定报价，提示是否显著偏离建议区间（不阻断）。 */
    @PostMapping("/deviation")
    public Result<PriceDeviationVO> deviation(@CurrentUser LoginUser user,
                                             @RequestBody PriceDeviationRequest req) {
        long price = req.getPrice() == null ? 0L : req.getPrice();
        return Result.ok(pricingService.deviation(req.getCategoryId(), req.getConditionLevel(), price));
    }
}
