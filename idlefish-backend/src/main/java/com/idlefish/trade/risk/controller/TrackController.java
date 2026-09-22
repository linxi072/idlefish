package com.idlefish.trade.risk.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.risk.service.TrackService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 埋点接口（需登录）：上报 12 类用户行为事件；并提供事件目录查询。
 */
@RestController
@RequestMapping("/api/track")
public class TrackController {

    private final TrackService trackService;

    public TrackController(TrackService trackService) {
        this.trackService = trackService;
    }

    /** 上报埋点事件。event ∈ {register,login,publish,view_item,search,favorite,order_create,pay,ship,confirm_receive,refund_apply,comment,share} */
    @PostMapping("/{event}")
    public Result<Void> track(@CurrentUser LoginUser loginUser,
                              @PathVariable String event,
                              @RequestParam(required = false) String bizId,
                              @RequestParam(required = false) String ext) {
        trackService.track(loginUser.getUserId(), event, bizId, ext);
        return Result.ok();
    }

    /** 埋点事件目录。 */
    @GetMapping("/catalog")
    public Result<List<String>> catalog() {
        return Result.ok(trackService.eventCatalog());
    }
}
