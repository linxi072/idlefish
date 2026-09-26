package com.idlefish.trade.favorite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.favorite.entity.Favorite;
import com.idlefish.trade.favorite.mapper.FavoriteMapper;
import com.idlefish.trade.favorite.vo.FavoriteVO;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.service.ItemService;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 收藏服务：收藏/取消切换、是否收藏、收藏列表（分页）。
 * 与商品收藏计数联动：收藏 +1、取消 -1（原子更新，下限 0）。
 */
@Service
public class FavoriteService {

    private static final DateTimeFormatter FMT = com.idlefish.trade.common.util.DateTimeUtil.FMT;

    private final FavoriteMapper favoriteMapper;
    private final ItemService itemService;
    private final ObjectMapper objectMapper;

    public FavoriteService(FavoriteMapper favoriteMapper, ItemService itemService, ObjectMapper objectMapper) {
        this.favoriteMapper = favoriteMapper;
        this.itemService = itemService;
        this.objectMapper = objectMapper;
    }

    /** 收藏/取消切换：已收藏则取消并减计数，未收藏则新增并加计数。返回操作后是否已收藏。 */
    public boolean toggle(Long userId, Long itemId) {
        itemService.view(itemId); // 校验商品存在
        LambdaQueryWrapper<Favorite> w = new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).eq(Favorite::getItemId, itemId);
        Favorite exist = favoriteMapper.selectOne(w);
        if (exist != null) {
            favoriteMapper.delete(w);
            itemService.changeFavCount(itemId, -1);
            return false;
        }
        Favorite f = new Favorite();
        f.setUserId(userId);
        f.setItemId(itemId);
        favoriteMapper.insert(f);
        itemService.changeFavCount(itemId, 1);
        return true;
    }

    /** 是否收藏。 */
    public boolean isFavorited(Long userId, Long itemId) {
        Long c = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).eq(Favorite::getItemId, itemId));
        return c != null && c > 0;
    }

    /** 我的收藏列表（分页）。 */
    public IPage<FavoriteVO> list(Long userId, int page, int size) {
        Page<Favorite> p = new Page<>(Math.max(page, 1), Math.max(size, 1));
        IPage<Favorite> result = favoriteMapper.selectPage(p, new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).orderByDesc(Favorite::getCreatedAt));
        return result.convert(this::toVO);
    }

    private FavoriteVO toVO(Favorite f) {
        FavoriteVO vo = new FavoriteVO();
        vo.setId(f.getId());
        vo.setItemId(f.getItemId());
        vo.setCreatedAt(f.getCreatedAt() == null ? null : f.getCreatedAt().format(FMT));
        try {
            Item item = itemService.view(f.getItemId());
            vo.setTitle(item.getTitle());
            vo.setCover(firstImage(item.getImages()));
            vo.setPrice(item.getPrice());
            vo.setPriceYuan(item.getPrice() == null ? null : item.getPrice() / 100.0);
        } catch (BizException ignore) {
            // 商品已删除不阻断列表
        }
        return vo;
    }

    private String firstImage(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) return null;
        try {
            List<String> list = objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {});
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            return null;
        }
    }
}
