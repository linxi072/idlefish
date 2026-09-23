package com.idlefish.trade.im.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.im.service.ImService;
import com.idlefish.trade.im.vo.ConversationVO;
import com.idlefish.trade.im.vo.MessageVO;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IM 接口（需登录）。发送消息支持文本/图片；会话列表、消息列表、未读数、已读。
 */
@RestController
@RequestMapping("/api/im")
public class ImController {

    private final ImService imService;

    public ImController(ImService imService) {
        this.imService = imService;
    }

    /** 发送消息。 */
    @PostMapping("/send")
    public Result<MessageVO> send(@CurrentUser LoginUser loginUser,
                                  @RequestParam Long receiverId,
                                  @RequestParam Long itemId,
                                  @RequestParam String content,
                                  @RequestParam(defaultValue = "text") String type) {
        return Result.ok(imService.send(loginUser.getUserId(), receiverId, itemId, content, type));
    }

    /** 平台客服会话入口（PRD §D4）：返回（或创建）用户与平台客服的会话。 */
    @PostMapping("/cs-entry")
    public Result<Map<String, Object>> csEntry(@CurrentUser LoginUser loginUser,
                                              @RequestParam(required = false) Long itemId) {
        String convId = imService.csConversation(loginUser.getUserId(), 10000L, itemId);
        Map<String, Object> data = new HashMap<>();
        data.put("convId", convId);
        data.put("peerId", 10000L);
        data.put("itemId", itemId);
        return Result.ok(data);
    }

    /** 会话列表。 */
    @GetMapping("/conversations")
    public Result<List<ConversationVO>> conversations(@CurrentUser LoginUser loginUser) {
        return Result.ok(imService.conversations(loginUser.getUserId()));
    }

    /** 会话消息列表（进入即已读）。 */
    @GetMapping("/messages")
    public Result<List<MessageVO>> messages(@CurrentUser LoginUser loginUser,
                                           @RequestParam String convId) {
        return Result.ok(imService.messages(loginUser.getUserId(), convId));
    }

    /** 未读总数。 */
    @GetMapping("/unread")
    public Result<Integer> unread(@CurrentUser LoginUser loginUser) {
        return Result.ok(imService.unread(loginUser.getUserId()));
    }
}
