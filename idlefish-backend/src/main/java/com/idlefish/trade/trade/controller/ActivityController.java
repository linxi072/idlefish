package com.idlefish.trade.trade.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.trade.dto.ActivityCreateDTO;
import com.idlefish.trade.trade.dto.ActivityJoinDTO;
import com.idlefish.trade.trade.entity.Activity;
import com.idlefish.trade.trade.entity.ActivityParticipant;
import com.idlefish.trade.trade.service.ActivityService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 促销活动接口（F-13.3 拼团/限时秒杀，需登录）。
 */
@RestController
@RequestMapping("/api/activity")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    /** 创建活动（运营/管理端）。 */
    @PostMapping("/create")
    public Result<Activity> create(@RequestBody ActivityCreateDTO dto) {
        return Result.ok(activityService.create(dto));
    }

    /** 进行中活动列表（发现页）。 */
    @GetMapping("/list")
    public Result<List<Activity>> list() {
        return Result.ok(activityService.listOngoing());
    }

    /** 活动详情。 */
    @GetMapping("/detail")
    public Result<Activity> detail(@RequestParam Long id) {
        return Result.ok(activityService.detail(id));
    }

    /** 参与活动（拼团可带 groupNo 加入已有团）。 */
    @PostMapping("/join")
    public Result<ActivityParticipant> join(@CurrentUser LoginUser loginUser, @RequestBody ActivityJoinDTO dto) {
        return Result.ok(activityService.join(loginUser.getUserId(), dto));
    }

    /** 我的活动参与记录。 */
    @GetMapping("/my")
    public Result<List<ActivityParticipant>> my(@CurrentUser LoginUser loginUser) {
        return Result.ok(activityService.myParticipants(loginUser.getUserId()));
    }
}
