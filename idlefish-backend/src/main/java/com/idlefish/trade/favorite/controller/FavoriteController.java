package com.idlefish.trade.favorite.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.favorite.service.FavoriteService;
import com.idlefish.trade.favorite.vo.FavoriteVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收藏接口（需登录）。
 */
@RestController
@RequestMapping("/api/favorite")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /** 收藏 / 取消收藏切换。 */
    @PostMapping("/toggle")
    public Result<Boolean> toggle(@CurrentUser LoginUser user, @RequestBody ToggleDTO dto) {
        return Result.ok(favoriteService.toggle(user.getUserId(), dto.getItemId()));
    }

    /** 是否收藏。 */
    @GetMapping("/check")
    public Result<Boolean> check(@CurrentUser LoginUser user, @RequestParam Long itemId) {
        return Result.ok(favoriteService.isFavorited(user.getUserId(), itemId));
    }

    /** 我的收藏列表（分页）。 */
    @GetMapping("/list")
    public Result<IPage<FavoriteVO>> list(@CurrentUser LoginUser user,
                                         @RequestParam(defaultValue = "1") Integer page,
                                         @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(favoriteService.list(user.getUserId(), page, size));
    }

    /** 切换入参。 */
    @lombok.Data
    public static class ToggleDTO {
        private Long itemId;
    }
}
