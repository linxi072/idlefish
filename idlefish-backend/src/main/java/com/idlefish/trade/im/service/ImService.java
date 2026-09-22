package com.idlefish.trade.im.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.im.entity.Conversation;
import com.idlefish.trade.im.entity.Message;
import com.idlefish.trade.im.mapper.ConversationMapper;
import com.idlefish.trade.im.mapper.MessageMapper;
import com.idlefish.trade.im.vo.ConversationVO;
import com.idlefish.trade.im.vo.MessageVO;
import com.idlefish.trade.im.ws.WsSessionManager;
import com.idlefish.trade.user.service.UserService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * IM 服务：一对一会话（买卖家围绕商品沟通）。
 * 发送消息 → 落库 → 更新会话 → 在线实时推送（离线由未读计数兜底）。
 */
@Service
public class ImService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final WsSessionManager wsSessionManager;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public ImService(ConversationMapper conversationMapper, MessageMapper messageMapper,
                     WsSessionManager wsSessionManager, UserService userService, ObjectMapper objectMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.wsSessionManager = wsSessionManager;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    /** 发送消息（sender→receiver，围绕 itemId）。 */
    public MessageVO send(Long senderId, Long receiverId, Long itemId, String content, String type) {
        if (receiverId == null || receiverId.equals(senderId)) {
            throw new com.idlefish.trade.common.BizException(com.idlefish.trade.common.Code.PARAM_INVALID, "接收人无效");
        }
        String convId = convIdOf(senderId, receiverId, itemId);
        Conversation conv = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>().eq(Conversation::getConvId, convId));
        if (conv == null) {
            conv = new Conversation();
            conv.setConvId(convId);
            conv.setBuyerId(Math.min(senderId, receiverId));
            conv.setSellerId(Math.max(senderId, receiverId));
            conv.setItemId(itemId);
            conv.setBuyerUnread(0);
            conv.setSellerUnread(0);
            conversationMapper.insert(conv);
        }

        Message msg = new Message();
        msg.setConvId(convId);
        msg.setSenderId(senderId);
        msg.setReceiverId(receiverId);
        msg.setType(type == null ? "text" : type);
        msg.setContent(content);
        msg.setReadFlag(0);
        messageMapper.insert(msg);

        // 更新会话摘要与未读
        Conversation upd = new Conversation();
        upd.setId(conv.getId());
        upd.setLastMessage("[图片]".equals(content) && "image".equals(msg.getType()) ? "[图片]" : content);
        upd.setLastSenderId(senderId);
        if (receiverId.equals(conv.getBuyerId())) {
            upd.setBuyerUnread((conv.getBuyerUnread() == null ? 0 : conv.getBuyerUnread()) + 1);
        } else {
            upd.setSellerUnread((conv.getSellerUnread() == null ? 0 : conv.getSellerUnread()) + 1);
        }
        conversationMapper.updateById(upd);

        // 实时推送
        push(receiverId, msg);

        return toVO(msg);
    }

    /** 当前用户会话列表（含未读）。 */
    public List<ConversationVO> conversations(Long userId) {
        List<Conversation> list = conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                .and(w -> w.eq(Conversation::getBuyerId, userId).or().eq(Conversation::getSellerId, userId))
                .orderByDesc(Conversation::getUpdatedAt));
        return list.stream().map(c -> {
            ConversationVO vo = new ConversationVO();
            vo.setConvId(c.getConvId());
            vo.setItemId(c.getItemId());
            vo.setLastMessage(c.getLastMessage());
            vo.setLastSenderId(c.getLastSenderId());
            vo.setLastTime(c.getUpdatedAt() == null ? null : c.getUpdatedAt().format(FMT));
            Long peer = c.getBuyerId().equals(userId) ? c.getSellerId() : c.getBuyerId();
            vo.setPeerId(peer);
            vo.setPeerName(userService.getById(peer) == null ? "" : userService.getById(peer).getNickname());
            vo.setUnread(c.getBuyerId().equals(userId) ? c.getBuyerUnread() : c.getSellerUnread());
            return vo;
        }).collect(Collectors.toList());
    }

    /** 会话消息列表（校验参与人）。 */
    public List<MessageVO> messages(Long userId, String convId) {
        Conversation conv = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>().eq(Conversation::getConvId, convId));
        if (conv == null || (!conv.getBuyerId().equals(userId) && !conv.getSellerId().equals(userId))) {
            throw new com.idlefish.trade.common.BizException(com.idlefish.trade.common.Code.STATE_NOT_ALLOWED, "无权访问该会话");
        }
        List<Message> msgs = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getConvId, convId).orderByAsc(Message::getCreatedAt));
        // 进入会话即标记已读
        markRead(userId, convId);
        return msgs.stream().map(this::toVO).collect(Collectors.toList());
    }

    /** 未读总数。 */
    public int unread(Long userId) {
        List<Conversation> list = conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                .and(w -> w.eq(Conversation::getBuyerId, userId).or().eq(Conversation::getSellerId, userId)));
        int total = 0;
        for (Conversation c : list) {
            if (c.getBuyerId().equals(userId)) {
                total += c.getBuyerUnread() == null ? 0 : c.getBuyerUnread();
            } else {
                total += c.getSellerUnread() == null ? 0 : c.getSellerUnread();
            }
        }
        return total;
    }

    /** 标记会话已读（清零对端未读）。 */
    public void markRead(Long userId, String convId) {
        Conversation conv = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>().eq(Conversation::getConvId, convId));
        if (conv == null) {
            return;
        }
        Conversation upd = new Conversation();
        upd.setId(conv.getId());
        if (conv.getBuyerId().equals(userId)) {
            upd.setBuyerUnread(0);
        } else {
            upd.setSellerUnread(0);
        }
        conversationMapper.updateById(upd);
        messageMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Message>()
                .eq(Message::getConvId, convId)
                .eq(Message::getReceiverId, userId)
                .set(Message::getReadFlag, 1));
    }

    private void push(Long receiverId, Message msg) {
        try {
            String payload = objectMapper.writeValueAsString(toVO(msg));
            wsSessionManager.send(receiverId, payload);
        } catch (Exception ignored) {
            // 序列化失败忽略
        }
    }

    private MessageVO toVO(Message m) {
        MessageVO vo = new MessageVO();
        vo.setConvId(m.getConvId());
        vo.setSenderId(m.getSenderId());
        vo.setReceiverId(m.getReceiverId());
        vo.setType(m.getType());
        vo.setContent(m.getContent());
        vo.setReadFlag(m.getReadFlag());
        vo.setCreatedAt(m.getCreatedAt() == null ? null : m.getCreatedAt().format(FMT));
        return vo;
    }

    private String convIdOf(Long a, Long b, Long itemId) {
        long min = Math.min(a, b);
        long max = Math.max(a, b);
        return "c_" + min + "_" + max + "_" + itemId;
    }
}
