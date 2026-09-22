package com.idlefish.trade.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.enums.ItemStatus;
import com.idlefish.trade.item.dto.ItemEditDTO;
import com.idlefish.trade.item.dto.ItemPublishDTO;
import com.idlefish.trade.item.dto.ItemQueryDTO;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.item.vo.ItemDetailVO;
import com.idlefish.trade.item.vo.ItemVO;
import com.idlefish.trade.item.vo.SellerVO;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.service.UserService;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 商品服务：发布、内容审核、详情聚合、检索、库存锁、状态流转。
 */
@Service
public class ItemService {

    private final ItemMapper itemMapper;
    private final CategoryService categoryService;
    private final UserService userService;
    private final ItemStateMachine stateMachine;
    private final ObjectMapper objectMapper;

    /** 单实例应用级库存锁（防超卖）；多实例需改用 Redis 分布式锁。 */
    private final ConcurrentMap<Long, Object> itemLocks = new ConcurrentHashMap<>();

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ItemService(ItemMapper itemMapper, CategoryService categoryService,
                       UserService userService, ItemStateMachine stateMachine, ObjectMapper objectMapper) {
        this.itemMapper = itemMapper;
        this.categoryService = categoryService;
        this.userService = userService;
        this.stateMachine = stateMachine;
        this.objectMapper = objectMapper;
    }

    /** 发布商品：初始为 DRAFT 草稿。 */
    public Long publish(Long sellerId, ItemPublishDTO dto) {
        categoryService.getById(dto.getCategoryId());
        Item item = new Item();
        item.setSellerId(sellerId);
        item.setCategoryId(dto.getCategoryId());
        item.setTitle(dto.getTitle());
        item.setDescription(dto.getDescription());
        item.setPrice(dto.getPrice());
        item.setOriginalPrice(dto.getOriginalPrice());
        item.setImages(toJson(dto.getImages()));
        item.setVideoUrl(dto.getVideoUrl());
        item.setConditionLevel(dto.getConditionLevel());
        item.setStock(dto.getStock());
        item.setProvince(dto.getProvince());
        item.setCity(dto.getCity());
        item.setFreight(dto.getFreight());
        item.setStatus(ItemStatus.DRAFT.getCode());
        item.setAuditStatus("pending");
        item.setViewCount(0);
        item.setLikeCount(0);
        item.setFavCount(0);
        itemMapper.insert(item);
        return item.getId();
    }

    /** 提交审核：草稿 → 待审核；本地模拟内容审核（默认通过 → 在售）。 */
    public void submitReview(Long sellerId, Long itemId) {
        Item item = mustOwn(sellerId, itemId);
        stateMachine.validate(ItemStatus.fromCode(item.getStatus()), ItemStatus.PENDING_REVIEW);
        boolean pass = auditContent(item);
        Item upd = new Item();
        upd.setId(itemId);
        if (pass) {
            upd.setStatus(ItemStatus.ONSALE.getCode());
            upd.setAuditStatus("pass");
        } else {
            upd.setStatus(ItemStatus.REJECTED.getCode());
            upd.setAuditStatus("reject");
            upd.setAuditReason("内容未通过审核");
        }
        itemMapper.updateById(upd);
    }

    /** 管理员审核（演示驳回/通过）。 */
    public void audit(Long itemId, boolean pass, String reason) {
        Item item = getById(itemId);
        stateMachine.validate(ItemStatus.fromCode(item.getStatus()),
                pass ? ItemStatus.ONSALE : ItemStatus.REJECTED);
        Item upd = new Item();
        upd.setId(itemId);
        if (pass) {
            upd.setStatus(ItemStatus.ONSALE.getCode());
            upd.setAuditStatus("pass");
        } else {
            upd.setStatus(ItemStatus.REJECTED.getCode());
            upd.setAuditStatus("reject");
            upd.setAuditReason(reason);
        }
        itemMapper.updateById(upd);
    }

    /** 编辑商品（覆盖非空字段）。 */
    public void edit(Long sellerId, ItemEditDTO dto) {
        Item item = mustOwn(sellerId, dto.getItemId());
        Item upd = new Item();
        upd.setId(dto.getItemId());
        if (dto.getCategoryId() != null) upd.setCategoryId(dto.getCategoryId());
        if (dto.getTitle() != null) upd.setTitle(dto.getTitle());
        if (dto.getDescription() != null) upd.setDescription(dto.getDescription());
        if (dto.getPrice() != null) upd.setPrice(dto.getPrice());
        if (dto.getOriginalPrice() != null) upd.setOriginalPrice(dto.getOriginalPrice());
        if (dto.getImages() != null) upd.setImages(toJson(dto.getImages()));
        if (dto.getVideoUrl() != null) upd.setVideoUrl(dto.getVideoUrl());
        if (dto.getConditionLevel() != null) upd.setConditionLevel(dto.getConditionLevel());
        if (dto.getStock() != null) upd.setStock(dto.getStock());
        if (dto.getProvince() != null) upd.setProvince(dto.getProvince());
        if (dto.getCity() != null) upd.setCity(dto.getCity());
        if (dto.getFreight() != null) upd.setFreight(dto.getFreight());
        itemMapper.updateById(upd);
    }

    public void offShelf(Long sellerId, Long itemId) {
        Item item = mustOwn(sellerId, itemId);
        stateMachine.validate(ItemStatus.fromCode(item.getStatus()), ItemStatus.OFF_SHELF);
        setStatus(itemId, ItemStatus.OFF_SHELF);
    }

    public void delete(Long sellerId, Long itemId) {
        Item item = mustOwn(sellerId, itemId);
        stateMachine.validate(ItemStatus.fromCode(item.getStatus()), ItemStatus.OFF_SHELF);
        setStatus(itemId, ItemStatus.OFF_SHELF);
    }

    /** 买家侧在售商品检索（status=onsale & 审核通过）。 */
    public IPage<ItemVO> buyerList(ItemQueryDTO q) {
        LambdaQueryWrapper<Item> w = new LambdaQueryWrapper<>();
        w.eq(Item::getStatus, ItemStatus.ONSALE.getCode());
        w.eq(Item::getAuditStatus, "pass");
        if (q.getCategoryId() != null) w.eq(Item::getCategoryId, q.getCategoryId());
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) w.like(Item::getTitle, q.getKeyword());
        if (q.getConditionLevel() != null) w.eq(Item::getConditionLevel, q.getConditionLevel());
        if (q.getMinPrice() != null) w.ge(Item::getPrice, q.getMinPrice());
        if (q.getMaxPrice() != null) w.le(Item::getPrice, q.getMaxPrice());
        switch (q.getSort() == null ? "" : q.getSort()) {
            case "price_asc": w.orderByAsc(Item::getPrice); break;
            case "price_desc": w.orderByDesc(Item::getPrice); break;
            case "hot": w.orderByDesc(Item::getViewCount); break;
            default: w.orderByDesc(Item::getCreatedAt);
        }
        Page<Item> page = new Page<>(Math.max(q.getPage(), 1), Math.max(q.getSize(), 1));
        return itemMapper.selectPage(page, w).convert(this::toVO);
    }

    /** 卖家侧商品列表（按状态过滤）。 */
    public IPage<ItemVO> listMine(Long sellerId, ItemQueryDTO q) {
        LambdaQueryWrapper<Item> w = new LambdaQueryWrapper<>();
        w.eq(Item::getSellerId, sellerId);
        if (q.getStatus() != null && !q.getStatus().isBlank()) w.eq(Item::getStatus, q.getStatus());
        w.orderByDesc(Item::getCreatedAt);
        Page<Item> page = new Page<>(Math.max(q.getPage(), 1), Math.max(q.getSize(), 1));
        return itemMapper.selectPage(page, w).convert(this::toVO);
    }

    /** 商品详情（聚合卖家 + 类目 + 反序列化图片），并自增浏览量。 */
    public ItemDetailVO detail(Long itemId) {
        Item item = getById(itemId);
        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, itemId).setSql("view_count = view_count + 1"));
        ItemDetailVO vo = new ItemDetailVO();
        org.springframework.beans.BeanUtils.copyProperties(toVO(item), vo);
        vo.setDescription(item.getDescription());
        vo.setImages(fromJson(item.getImages()));
        vo.setVideoUrl(item.getVideoUrl());
        vo.setStock(item.getStock());
        return vo;
    }

    /** 下单锁库存：扣减库存，售罄转 LOCKED。返回是否成功。 */
    public void lockStock(Long itemId, int qty) {
        Object lock = itemLocks.computeIfAbsent(itemId, k -> new Object());
        synchronized (lock) {
            Item item = getById(itemId);
            if (!ItemStatus.ONSALE.getCode().equals(item.getStatus())) {
                throw new BizException(Code.STATE_NOT_ALLOWED, "商品当前不可购买");
            }
            if (item.getStock() < qty) {
                throw new BizException(Code.STOCK_NOT_ENOUGH);
            }
            Item upd = new Item();
            upd.setId(itemId);
            upd.setStock(item.getStock() - qty);
            if (upd.getStock() == 0) {
                upd.setStatus(ItemStatus.LOCKED.getCode());
            }
            if (itemMapper.updateById(upd) == 0) {
                throw new BizException(Code.STOCK_NOT_ENOUGH, "库存扣减失败，请重试");
            }
        }
    }

    /** 取消订单释放库存：恢复库存；若由 LOCKED 恢复则回 ONSALE。 */
    public void releaseStock(Long itemId, int qty) {
        Object lock = itemLocks.computeIfAbsent(itemId, k -> new Object());
        synchronized (lock) {
            Item item = getById(itemId);
            Item upd = new Item();
            upd.setId(itemId);
            upd.setStock(item.getStock() + qty);
            if (ItemStatus.LOCKED.getCode().equals(item.getStatus()) && upd.getStock() > 0) {
                upd.setStatus(ItemStatus.ONSALE.getCode());
            }
            itemMapper.updateById(upd);
        }
    }

    /** 确认收货后标记成交（库存已为 0）。 */
    public void markSold(Long itemId) {
        Item item = getById(itemId);
        stateMachine.validate(ItemStatus.fromCode(item.getStatus()), ItemStatus.SOLD);
        setStatus(itemId, ItemStatus.SOLD);
    }

    /** 跨模块只读读取商品实体（收藏服务等调用）。 */
    public Item view(Long itemId) {
        return getById(itemId);
    }

    /** 原子调整商品收藏计数（下限 0）。 */
    public void changeFavCount(Long itemId, int delta) {
        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, itemId)
                .setSql("fav_count = GREATEST(0, fav_count + " + delta + ")"));
    }

    /** 商品快照（写入订单，防止商品信息变更影响履约）。 */
    public String itemSnapshot(Long itemId) {
        Item item = getById(itemId);
        List<String> imgs = fromJson(item.getImages());
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("title", item.getTitle());
        m.put("cover", imgs.isEmpty() ? null : imgs.get(0));
        m.put("price", item.getPrice());
        m.put("originalPrice", item.getOriginalPrice());
        m.put("conditionLevel", item.getConditionLevel());
        m.put("categoryId", item.getCategoryId());
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            throw new BizException(Code.SYSTEM_ERROR, "商品快照生成失败");
        }
    }

    // ---------- 内部工具 ----------

    private Item getById(Long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BizException(Code.ITEM_NOT_FOUND);
        }
        return item;
    }

    private Item mustOwn(Long sellerId, Long itemId) {
        Item item = getById(itemId);
        if (!item.getSellerId().equals(sellerId)) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "非商品所有者，禁止操作");
        }
        return item;
    }

    private void setStatus(Long itemId, ItemStatus status) {
        Item upd = new Item();
        upd.setId(itemId);
        upd.setStatus(status.getCode());
        itemMapper.updateById(upd);
    }

    /** 模拟内容审核：含敏感词则驳回。 */
    private boolean auditContent(Item item) {
        if (item.getTitle() == null) return false;
        String[] blocked = {"代开发票", "色情", "赌博", "枪支"};
        for (String w : blocked) {
            if (item.getTitle().contains(w) || (item.getDescription() != null && item.getDescription().contains(w))) {
                return false;
            }
        }
        return true;
    }

    private ItemVO toVO(Item item) {
        ItemVO vo = new ItemVO();
        vo.setId(item.getId());
        vo.setSellerId(item.getSellerId());
        vo.setCategoryId(item.getCategoryId());
        vo.setCategoryName(categoryService.nameOf(item.getCategoryId()));
        vo.setTitle(item.getTitle());
        List<String> imgs = fromJson(item.getImages());
        vo.setCover(imgs.isEmpty() ? null : imgs.get(0));
        vo.setPrice(item.getPrice());
        vo.setOriginalPrice(item.getOriginalPrice());
        vo.setPriceYuan(fenToYuan(item.getPrice()));
        vo.setOriginalPriceYuan(fenToYuan(item.getOriginalPrice()));
        vo.setFreightYuan(fenToYuan(item.getFreight()));
        vo.setConditionLevel(item.getConditionLevel());
        vo.setStatus(item.getStatus());
        vo.setAuditStatus(item.getAuditStatus());
        vo.setCity(item.getCity());
        vo.setFreight(item.getFreight());
        vo.setViewCount(item.getViewCount());
        vo.setLikeCount(item.getLikeCount());
        vo.setFavCount(item.getFavCount());
        vo.setCreatedAt(item.getCreatedAt() == null ? null : item.getCreatedAt().format(FMT));
        if (item.getSellerId() != null) {
            try {
                User u = userService.getById(item.getSellerId());
                vo.setSellerName(u.getNickname());
                vo.setSellerAvatar(u.getAvatar());
                SellerVO sv = new SellerVO();
                sv.setId(u.getId());
                sv.setNickname(u.getNickname());
                sv.setAvatar(u.getAvatar());
                sv.setCreditScore(u.getCreditScore());
                sv.setRealNameVerified(u.getRealNameVerified() == null ? 0 : u.getRealNameVerified());
                vo.setSeller(sv);
            } catch (BizException ignore) {
                // 卖家不存在时不阻断列表
            }
        }
        return vo;
    }

    private Double fenToYuan(Long fen) {
        return fen == null ? null : fen / 100.0;
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? List.of() : list);
        } catch (Exception e) {
            throw new BizException(Code.SYSTEM_ERROR, "图片序列化失败");
        }
    }

    private List<String> fromJson(String s) {
        if (s == null || s.isBlank()) return List.of();
        try {
            return objectMapper.readValue(s, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
