package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.NotificationDelivery;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知投递记录 Mapper（T-27 设计文档 §6.3）。
 * <p>
 * 落记录、取到期重试集与保留清理均由 {@code service.impl} 以条件包装器表达，无需自定义 SQL。
 */
@Mapper
public interface NotificationDeliveryMapper extends BaseMapper<NotificationDelivery> {
}