package com.idlefish.trade.user.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.user.dto.AddressDTO;
import com.idlefish.trade.user.service.AddressService;
import com.idlefish.trade.user.vo.AddressVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public Result<List<AddressVO>> list(@CurrentUser LoginUser user) {
        return Result.ok(addressService.list(user.getUserId()));
    }

    @PostMapping
    public Result<Long> create(@CurrentUser LoginUser user, @Valid @RequestBody AddressDTO dto) {
        return Result.ok(addressService.create(user.getUserId(), dto));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@CurrentUser LoginUser user,
                               @PathVariable Long id,
                               @Valid @RequestBody AddressDTO dto) {
        addressService.update(user.getUserId(), id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@CurrentUser LoginUser user, @PathVariable Long id) {
        addressService.delete(user.getUserId(), id);
        return Result.ok();
    }

    @PostMapping("/{id}/default")
    public Result<Void> setDefault(@CurrentUser LoginUser user, @PathVariable Long id) {
        addressService.setDefault(user.getUserId(), id);
        return Result.ok();
    }
}
