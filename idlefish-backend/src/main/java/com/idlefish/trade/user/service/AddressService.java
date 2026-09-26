package com.idlefish.trade.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.util.CryptoUtil;
import com.idlefish.trade.user.dto.AddressDTO;
import com.idlefish.trade.user.entity.Address;
import com.idlefish.trade.user.mapper.AddressMapper;
import com.idlefish.trade.user.vo.AddressVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 收货地址服务：CRUD、默认地址互斥、订单地址快照。
 */
@Service
public class AddressService {

    private final AddressMapper addressMapper;
    private final CryptoUtil cryptoUtil;
    private static final ObjectMapper OM = new ObjectMapper();

    public AddressService(AddressMapper addressMapper, CryptoUtil cryptoUtil) {
        this.addressMapper = addressMapper;
        this.cryptoUtil = cryptoUtil;
    }

    public List<AddressVO> list(Long userId) {
        List<Address> list = addressMapper.selectList(
                new LambdaQueryWrapper<Address>().eq(Address::getUserId, userId)
                        .orderByDesc(Address::getIsDefault).orderByDesc(Address::getCreatedAt));
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    public Long create(Long userId, AddressDTO dto) {
        if (Boolean.TRUE.equals(toBool(dto.getIsDefault()))) {
            clearDefault(userId);
        }
        Address a = new Address();
        BeanUtils.copyProperties(dto, a);
        a.setUserId(userId);
        a.setPhone(cryptoUtil.encrypt(dto.getPhone()));
        if (a.getIsDefault() == null) {
            a.setIsDefault(0);
        }
        addressMapper.insert(a);
        return a.getId();
    }

    public void update(Long userId, Long id, AddressDTO dto) {
        Address exist = owned(userId, id);
        BeanUtils.copyProperties(dto, exist);
        if (Boolean.TRUE.equals(toBool(dto.getIsDefault()))) {
            clearDefault(userId);
        }
        exist.setPhone(cryptoUtil.encrypt(dto.getPhone()));
        addressMapper.updateById(exist);
    }

    public void delete(Long userId, Long id) {
        owned(userId, id);
        addressMapper.deleteById(id);
    }

    public void setDefault(Long userId, Long id) {
        owned(userId, id);
        clearDefault(userId);
        Address a = new Address();
        a.setId(id);
        a.setIsDefault(1);
        addressMapper.updateById(a);
    }

    /** 生成订单地址快照（JSON，含真实可履约信息）。 */
    public String snapshot(Long id) {
        Address a = addressMapper.selectById(id);
        if (a == null) {
            return null;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("receiverName", a.getReceiverName());
        m.put("phone", cryptoUtil.decrypt(a.getPhone()));
        m.put("province", a.getProvince());
        m.put("city", a.getCity());
        m.put("district", a.getDistrict());
        m.put("detail", a.getDetail());
        try {
            return OM.writeValueAsString(m);
        } catch (Exception e) {
            throw new BizException(Code.SYSTEM_ERROR, "地址快照生成失败");
        }
    }

    private Address owned(Long userId, Long id) {
        Address a = addressMapper.selectById(id);
        if (a == null || !a.getUserId().equals(userId)) {
            throw new BizException(40006, "地址不存在或无权限");
        }
        return a;
    }

    private void clearDefault(Long userId) {
        addressMapper.update(new Address(),
                new UpdateWrapper<Address>().set("is_default", 0).eq("user_id", userId));
    }

    private AddressVO toVO(Address a) {
        AddressVO vo = new AddressVO();
        BeanUtils.copyProperties(a, vo);
        vo.setPhone(CryptoUtil.maskPhone(cryptoUtil.decrypt(a.getPhone())));
        return vo;
    }

    private Boolean toBool(Integer v) {
        return v != null && v == 1;
    }
}
