package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.DeviceCertificate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 设备证书 Mapper（T-25）。
 * <p>
 * 查询集中在「按设备取当前有效证书」这一条热路径上：认证回调的 X.509 分支据此判定
 * 设备是否持有未吊销且未过期的证书，吊销则据此定位待更新的行。
 */
@Mapper
public interface DeviceCertificateMapper extends BaseMapper<DeviceCertificate> {

    /**
     * 取设备当前有效证书：未吊销行中签发时间最新的一条。
     * <p>
     * 同一设备重复签发会留下多行，最新行即当前生效证书；已吊销行由 {@code revoked_at IS NULL} 排除。
     * 是否过期由调用方判定，避免把时间语义散落在 SQL 与 Java 两处。
     */
    @Select("SELECT * FROM device_certificate WHERE device_id = #{deviceId} AND revoked_at IS NULL "
            + "ORDER BY issued_at DESC, id DESC LIMIT 1")
    DeviceCertificate selectActiveByDeviceId(@Param("deviceId") Long deviceId);
}
