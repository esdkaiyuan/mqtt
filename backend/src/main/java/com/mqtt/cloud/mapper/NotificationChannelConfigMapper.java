package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.NotificationChannelConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知渠道配置 Mapper（T-27 设计文档 §6.2）。
 * <p>
 * 条件查询、计数与更新均由 {@code service.impl} 以条件包装器表达，无需自定义 SQL。
 */
@Mapper
public interface NotificationChannelConfigMapper extends BaseMapper<NotificationChannelConfig> {
}