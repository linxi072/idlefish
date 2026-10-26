package com.idlefish.trade.marketing.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.marketing.activity.entity.ActivityRecord;
import com.idlefish.trade.marketing.activity.service.ActivityKernelService;
import com.idlefish.trade.marketing.activity.service.MktBargainService;
import com.idlefish.trade.marketing.activity.service.LiveSeckillService;
import com.idlefish.trade.marketing.activity.service.LotteryService;
import com.idlefish.trade.marketing.activity.vo.ActivityJoinResultVO;
import com.idlefish.trade.marketing.activity.vo.BargainAssistVO;
import com.idlefish.trade.marketing.activity.vo.BargainSessionVO;
import com.idlefish.trade.marketing.activity.vo.MarketingActivityVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 营销活动用户端接口（ITER-F08 / REQ-01、REQ-02、REQ-03、REQ-06、REQ-08、REQ-09、REQ-10、REQ-13）。
 * 三类玩法（砍价 / 抽奖 / 直播秒杀）统一经活动内核，本控制器仅做参数解析与编排。
 * 类名显式加 Mkt 前缀，避免与 trade.controller.ActivityController（拼团/秒杀遗留）bean 名冲突。
 */
@RestController
@RequestMapping("/api/mkt/activity")
public class MktActivityController {

    private final ActivityKernelService kernel;
    private final MktBargainService bargainService;
    private final LotteryService lotteryService;
    private final LiveSeckillService liveSeckillService;

    public MktActivityController(ActivityKernelService kernel, MktBargainService bargainService,
                                 LotteryService lotteryService, LiveSeckillService liveSeckillService) {
        this.kernel = kernel;
        this.bargainService = bargainService;
        this.lotteryService = lotteryService;
        this.liveSeckillService = liveSeckillService;
    }

    /** 活动列表（仅进行中 + 人群定向可见，REQ-13）。 */
    @GetMapping("/list")
    public Result<List<MarketingActivityVO>> list(@CurrentUser LoginUser user,
                                                 @RequestParam(required = false) String kind) {
        return Result.ok(kernel.listActivities(kind, user.getUserId()));
    }

    /** 活动详情（含当前用户是否已参与）。 */
    @GetMapping("/{id}")
    public Result<MarketingActivityVO> detail(@CurrentUser LoginUser user, @PathVariable Long id) {
        return Result.ok(kernel.getActivity(id, user.getUserId()));
    }

    /** 砍价：发起砍价。 */
    @PostMapping("/bargain/create")
    public Result<BargainSessionVO> createBargain(@CurrentUser LoginUser user,
                                                  @RequestParam Long activityId,
                                                  @RequestParam(required = false) String deviceId) {
        return Result.ok(bargainService.createSession(user.getUserId(), deviceId, activityId));
    }

    /** 砍价：好友助力。 */
    @PostMapping("/bargain/assist")
    public Result<BargainAssistVO> assist(@CurrentUser LoginUser user,
                                         @RequestParam String sessionNo,
                                         @RequestParam(required = false) String deviceId) {
        return Result.ok(bargainService.assist(sessionNo, user.getUserId(), deviceId));
    }

    /** 砍价：会话详情 / 进度。 */
    @GetMapping("/bargain/{sessionNo}")
    public Result<BargainSessionVO> bargainDetail(@PathVariable String sessionNo) {
        return Result.ok(bargainService.getSession(sessionNo));
    }

    /** 抽奖：抽一次。 */
    @PostMapping("/lottery/draw")
    public Result<ActivityJoinResultVO> draw(@CurrentUser LoginUser user,
                                            @RequestParam Long activityId,
                                            @RequestParam(required = false) String deviceId) {
        return Result.ok(lotteryService.draw(user.getUserId(), deviceId, activityId));
    }

    /** 抽奖：高价值/实物奖品补充实名与收货信息（REQ-10）。 */
    @PostMapping("/lottery/realname")
    public Result<ActivityJoinResultVO> realname(@CurrentUser LoginUser user,
                                                @RequestParam String recordNo,
                                                @RequestParam String realName,
                                                @RequestParam String idNo,
                                                @RequestParam String address) {
        return Result.ok(lotteryService.submitRealname(recordNo, user.getUserId(), realName, idNo, address));
    }

    /** 直播秒杀：抢购（缓存预扣 + 异步落库，REQ-07）。 */
    @PostMapping("/seckill/grab")
    public Result<ActivityJoinResultVO> grab(@CurrentUser LoginUser user,
                                            @RequestParam Long activityId,
                                            @RequestParam(required = false) String deviceId,
                                            @RequestParam(required = false) String requestId) {
        return Result.ok(liveSeckillService.grab(user.getUserId(), deviceId, activityId, requestId));
    }

    /** 我的活动参与记录。 */
    @GetMapping("/my-records")
    public Result<List<ActivityRecord>> myRecords(@CurrentUser LoginUser user) {
        return Result.ok(kernel.myRecords(user.getUserId()));
    }
}
