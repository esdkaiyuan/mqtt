package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站内信 Mapper（T-27 设计文档 §6.1）。
 * <p>
 * 查询与状态迁移均由 {@code service.impl} 以条件包装器表达（未读过滤、置已读仅对 {@code read_at IS NULL} 生效），
 * 无需自定义 SQL，故此处仅声明基础 Mapper。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}