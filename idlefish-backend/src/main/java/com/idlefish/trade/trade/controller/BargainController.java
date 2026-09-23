package com.idlefish.trade.trade.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.trade.dto.BargainCreateDTO;
import com.idlefish.trade.trade.entity.Bargain;
import com.idlefish.trade.trade.service.BargainService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 议价接口（需登录，PRD §D3）。
 */
@RestController
@RequestMapping("/api/bargain")
public class BargainController {

    private final BargainService bargainService;

    public BargainController(BargainService bargainService) {
        this.bargainService = bargainService;
    }

    /** 发起议价。 */
    @PostMapping("/create")
    public Result<Bargain> create(@CurrentUser LoginUser loginUser, @RequestBody BargainCreateDTO dto) {
        return Result.ok(bargainService.create(loginUser.getUserId(), dto.getSellerId(),
                dto.getItemId(), dto.getConvId(), dto.getOfferPrice()));
    }

    /** 卖家接受议价（同步成交价）。 */
    @PostMapping("/accept")
    public Result<Bargain> accept(@CurrentUser LoginUser loginUser, @RequestParam Long bargainId) {
        return Result.ok(bargainService.accept(bargainId, loginUser.getUserId()));
    }

    /** 会话内议价列表。 */
    @GetMapping("/list")
    public Result<List<Bargain>> list(@RequestParam String convId) {
        return Result.ok(bargainService.listByConv(convId));
    }
}
