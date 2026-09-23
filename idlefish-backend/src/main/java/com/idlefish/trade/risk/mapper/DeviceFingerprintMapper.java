package com.idlefish.trade.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.risk.entity.DeviceFingerprint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 设备指纹 Mapper。
 */
@Mapper
public interface DeviceFingerprintMapper extends BaseMapper<DeviceFingerprint> {

    @Select("SELECT COUNT(DISTINCT user_id) FROM t_device_fingerprint WHERE device_id = #{deviceId}")
    int countUsersByDevice(@Param("deviceId") String deviceId);

    @Select("SELECT MIN(first_seen) FROM t_device_fingerprint WHERE device_id = #{deviceId}")
    LocalDateTime earliestSeen(@Param("deviceId") String deviceId);

    @Select("SELECT * FROM t_device_fingerprint WHERE device_id = #{deviceId} AND user_id = #{userId} LIMIT 1")
    DeviceFingerprint selectByDeviceUser(@Param("deviceId") String deviceId, @Param("userId") Long userId);
}
