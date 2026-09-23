package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.trade.entity.Bargain;
import com.idlefish.trade.trade.mapper.BargainMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 议价服务（PRD §D3）：发起议价、卖家接受（同步成交价）、24h 有效期、列表与超时失效。
 */
@Service
public class BargainService {

    /** 议价有效期：24 小时。 */
    private static final int EXPIRE_HOURS = 24;

    private final BargainMapper bargainMapper;
    private final ItemMapper itemMapper;

    public BargainService(BargainMapper bargainMapper, ItemMapper itemMapper) {
        this.bargainMapper = bargainMapper;
        this.itemMapper = itemMapper;
    }

    /** 买家发起议价（围绕商品会话）。 */
    public Bargain create(Long buyerId, Long sellerId, Long itemId, String convId, Long offerPrice) {
        if (buyerId == null || sellerId == null || buyerId.equals(sellerId)) {
            throw new BizException(Code.PARAM_INVALID, "议价双方无效");
        }
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BizException(Code.ITEM_NOT_FOUND);
        }
        Bargain b = new Bargain();
        b.setConvId(convId);
        b.setItemId(itemId);
        b.setBuyerId(buyerId);
        b.setSellerId(sellerId);
        b.setOriginPrice(item.getPrice());
        b.setOfferPrice(offerPrice);
        b.setStatus("pending");
        b.setExpireAt(LocalDateTime.now().plusHours(EXPIRE_HOURS));
        bargainMapper.insert(b);
        return b;
    }

    /** 卖家接受议价：同步成交价（覆盖原价，供下单时使用）。 */
    public Bargain accept(Long bargainId, Long operatorId) {
        Bargain b = bargainMapper.selectById(bargainId);
        if (b == null) {
            throw new BizException(Code.NOT_FOUND, "议价不存在");
        }
        if (!b.getSellerId().equals(operatorId)) {
            throw new BizException(Code.FORBIDDEN, "仅卖家可接受议价");
        }
        if (!"pending".equals(b.getStatus())) {
            throw new BizException(Code.BARGAIN_EXPIRED, "该议价已不可接受");
        }
        if (b.getExpireAt() != null && b.getExpireAt().isBefore(LocalDateTime.now())) {
            Bargain upd = new Bargain();
            upd.setId(b.getId());
            upd.setStatus("expired");
            bargainMapper.updateById(upd);
            throw new BizException(Code.BARGAIN_EXPIRED, "议价已超时失效");
        }
        Bargain upd = new Bargain();
        upd.setId(b.getId());
        upd.setStatus("accepted");
        bargainMapper.updateById(upd);

        // 同步成交价到商品（卖家接受即同意以出价成交）
        Item itemUpd = new Item();
        itemUpd.setId(b.getItemId());
        itemUpd.setPrice(b.getOfferPrice());
        itemMapper.updateById(itemUpd);

        return bargainMapper.selectById(bargainId);
    }

    /** 会话内议价列表（按时间倒序）。 */
    public List<Bargain> listByConv(String convId) {
        return bargainMapper.selectList(new LambdaQueryWrapper<Bargain>()
                .eq(Bargain::getConvId, convId).orderByDesc(Bargain::getCreatedAt));
    }

    /** 定时：失效过期未处理的议价。 */
    @Scheduled(fixedDelay = 600_000)
    public void expireOverdue() {
        try {
            bargainMapper.update(null, new LambdaUpdateWrapper<Bargain>()
                    .eq(Bargain::getStatus, "pending")
                    .lt(Bargain::getExpireAt, LocalDateTime.now())
                    .set(Bargain::getStatus, "expired"));
        } catch (Exception ignored) {
            // 调度容错
        }
    }
}
