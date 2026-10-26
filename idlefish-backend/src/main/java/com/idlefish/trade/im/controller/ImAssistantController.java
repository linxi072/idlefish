package com.idlefish.trade.im.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.im.service.ImAssistantService;
import com.idlefish.trade.im.service.ImService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * IM 智能助手用户端接口（ITER-F06，需登录）。
 * <ul>
 *   <li>转人工（REQ-09）：置位人工接管，会话停止自动作答，并下发带身份明示的提示；</li>
 *   <li>卖家会话级代答开关 / 解除接管；</li>
 *   <li>自动应答「有用/没用」反馈（REQ-14：点踩入未命中池）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/im/assistant")
public class ImAssistantController {

    private final ImAssistantService assistantService;
    private final ImService imService;

    public ImAssistantController(ImAssistantService assistantService, ImService imService) {
        this.assistantService = assistantService;
        this.imService = imService;
    }

    /** 转人工：接管会话并返回提示消息（首次接管时由服务端下发，已接管则不再重复下发）。 */
    @PostMapping("/human")
    public Result<Map<String, Object>> human(@CurrentUser LoginUser loginUser,
                                             @RequestParam String convId) {
        ImAssistantService.EscalationResult result = assistantService.requestHuman(loginUser.getUserId(), convId);
        if (result.notice() != null && !result.notice().isBlank()) {
            imService.sendAssistantReply(result.peerId(), result.selfId(), result.itemId(), result.notice());
        }
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("convId", convId);
        data.put("humanTakeover", true);
        data.put("summary", result.summary());
        return Result.ok(data);
    }

    /** 卖家会话级自动代答开关（仅卖家可操作）。 */
    @PostMapping("/auto-reply")
    public Result<Void> autoReply(@CurrentUser LoginUser loginUser,
                                  @RequestParam String convId,
                                  @RequestParam boolean enabled) {
        assistantService.setAutoReplyEnabled(loginUser.getUserId(), convId, enabled);
        return Result.ok(null);
    }

    /** 解除人工接管，恢复自动代答（仅卖家可操作）。 */
    @PostMapping("/takeover/clear")
    public Result<Void> clearTakeover(@CurrentUser LoginUser loginUser,
                                      @RequestParam String convId) {
        assistantService.clearTakeover(loginUser.getUserId(), convId);
        return Result.ok(null);
    }

    /** 会话助手状态（前端据此展示「转人工」按钮与代答标识）。 */
    @GetMapping("/status")
    public Result<Map<String, Object>> status(@CurrentUser LoginUser loginUser,
                                              @RequestParam String convId) {
        return Result.ok(assistantService.status(loginUser.getUserId(), convId));
    }

    /** 自动应答反馈：USEFUL / USELESS（点踩入未命中问题池，REQ-14）。 */
    @PostMapping("/feedback")
    public Result<Void> feedback(@CurrentUser LoginUser loginUser,
                                 @RequestParam Long replyId,
                                 @RequestParam String feedback) {
        assistantService.feedback(loginUser.getUserId(), replyId, feedback);
        return Result.ok(null);
    }
}
