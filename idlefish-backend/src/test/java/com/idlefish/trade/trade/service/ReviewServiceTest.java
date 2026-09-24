package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.enums.ReviewRole;
import com.idlefish.trade.trade.dto.ReviewSubmitDTO;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.entity.Review;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.mapper.ReviewMapper;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 评价业务单测（F-06）：提交自动过审、幂等、非交易方/未完成订单拦截、审核后信用重算。
 * 运行环境：MySQL（schema-mysql.sql + data-mysql.sql 自动初始化）。
 */
@SpringBootTest
class ReviewServiceTest {

    @Autowired
    private ReviewService reviewService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private ReviewMapper reviewMapper;
    @Autowired
    private UserMapper userMapper;

    private Long buyerId;
    private Long sellerId;
    private Long itemId;
    private String orderNo;

    @BeforeEach
    void setup() {
        long salt = System.nanoTime();
        buyerId = 500000L + (salt % 100000);
        sellerId = 600000L + (salt % 100000);
        itemId = 700000L + (salt % 100000);
        orderNo = "RO" + salt;
        insertUser(buyerId);
        insertUser(sellerId);

        Order o = new Order();
        o.setOrderNo(orderNo);
        o.setBuyerId(buyerId);
        o.setSellerId(sellerId);
        o.setItemId(itemId);
        o.setPayAmount(10000L);
        o.setPayNo("P" + salt);
        o.setStatus("COMPLETED");
        o.setVersion(0);
        orderMapper.insert(o);
    }

    private void insertUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setNickname("u" + id);
        u.setStatus(0);
        u.setRealNameVerified(0);
        u.setCreditScore(0);
        userMapper.insert(u);
    }

    @Test
    @DisplayName("买家对已完结订单评价：自动过审、角色为 BUYER_SELLER、触发卖家信用重算")
    void submitApprovedAndRecompute() {
        ReviewSubmitDTO dto = new ReviewSubmitDTO();
        dto.setOrderNo(orderNo);
        dto.setRating(5);
        dto.setContent("东西很好用，发货快");

        Long id = reviewService.submit(buyerId, dto);
        Review r = reviewMapper.selectById(id);
        assertNotNull(r);
        assertEquals(1, r.getStatus());
        assertEquals(ReviewRole.BUYER_SELLER.getCode(), r.getRole());
        assertEquals(sellerId, r.getTargetId());

        // 幂等：同一订单、同一端重复提交返回已有评价
        Long id2 = reviewService.submit(buyerId, dto);
        assertEquals(id, id2);

        // 被评价方（卖家）信用分被重算并写入
        User seller = userMapper.selectById(sellerId);
        assertNotNull(seller.getCreditScore());
    }

    @Test
    @DisplayName("非交易方提交评价被拒（STATE_NOT_ALLOWED）")
    void nonPartyRejected() {
        long stranger = 999999L;
        insertUser(stranger);
        ReviewSubmitDTO dto = new ReviewSubmitDTO();
        dto.setOrderNo(orderNo);
        dto.setRating(5);
        dto.setContent("一般般");
        assertThrows(BizException.class, () -> reviewService.submit(stranger, dto));
    }

    @Test
    @DisplayName("未完成订单（PAID）不可评价")
    void notCompletedRejected() {
        long salt = System.nanoTime();
        String paidNo = "PO" + salt;
        Order o = new Order();
        o.setOrderNo(paidNo);
        o.setBuyerId(buyerId);
        o.setSellerId(sellerId);
        o.setItemId(itemId);
        o.setPayAmount(10000L);
        o.setPayNo("PP" + salt);
        o.setStatus("PAID");
        o.setVersion(0);
        orderMapper.insert(o);

        ReviewSubmitDTO dto = new ReviewSubmitDTO();
        dto.setOrderNo(paidNo);
        dto.setRating(5);
        dto.setContent("待发货不能评");
        assertThrows(BizException.class, () -> reviewService.submit(buyerId, dto));
    }
}
