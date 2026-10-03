package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.OtaFirmware;
import org.apache.ibatis.annotations.Mapper;

/**
 * OTA 固件包 Mapper（T-22 设计文档 §7.1）。
 * <p>
 * 纯单表 CRUD，直接继承 {@code BaseMapper}，无自定义 SQL、无 XML。
 * 同产品下版本唯一由表级 {@code uk_product_version} 兜底：服务层先查后插，
 * 并在捕获 {@code DuplicateKeyException} 时抛 {@code OTA_FIRMWARE_DUPLICATE(6233)}，
 * 以此避免并发上传时的检查-写入竞态。
 */
@Mapper
public interface OtaFirmwareMapper extends BaseMapper<OtaFirmware> {
}
