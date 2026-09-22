package com.idlefish.trade.item.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.item.dto.ItemEditDTO;
import com.idlefish.trade.item.dto.ItemPublishDTO;
import com.idlefish.trade.item.dto.ItemQueryDTO;
import com.idlefish.trade.item.service.ItemService;
import com.idlefish.trade.item.vo.ItemDetailVO;
import com.idlefish.trade.item.vo.ItemVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 商品接口：买家只读（免登录）/ 卖家写操作（需登录）。
 */
@RestController
@RequestMapping("/api/item")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /** 发布商品（草稿）。 */
    @PostMapping("/publish")
    public Result<Long> publish(@CurrentUser LoginUser user, @Valid @RequestBody ItemPublishDTO dto) {
        return Result.ok(itemService.publish(user.getUserId(), dto));
    }

    /** 提交上架（触发审核）。 */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@CurrentUser LoginUser user, @PathVariable Long id) {
        itemService.submitReview(user.getUserId(), id);
        return Result.ok();
    }

    /** 编辑商品。 */
    @PutMapping("/edit")
    public Result<Void> edit(@CurrentUser LoginUser user, @RequestBody ItemEditDTO dto) {
        itemService.edit(user.getUserId(), dto);
        return Result.ok();
    }

    /** 下架。 */
    @PostMapping("/{id}/off-shelf")
    public Result<Void> offShelf(@CurrentUser LoginUser user, @PathVariable Long id) {
        itemService.offShelf(user.getUserId(), id);
        return Result.ok();
    }

    /** 删除（软删除）。 */
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@CurrentUser LoginUser user, @PathVariable Long id) {
        itemService.delete(user.getUserId(), id);
        return Result.ok();
    }

    /** 卖家商品列表。 */
    @GetMapping("/mine")
    public Result<IPage<ItemVO>> mine(@CurrentUser LoginUser user, ItemQueryDTO q) {
        return Result.ok(itemService.listMine(user.getUserId(), q));
    }

    /** 买家侧在售商品检索（免登录）。 */
    @GetMapping("/buyer/list")
    public Result<IPage<ItemVO>> buyerList(ItemQueryDTO q) {
        return Result.ok(itemService.buyerList(q));
    }

    /** 商品详情（免登录）。 */
    @GetMapping("/detail/{id}")
    public Result<ItemDetailVO> detail(@PathVariable Long id) {
        return Result.ok(itemService.detail(id));
    }
}
