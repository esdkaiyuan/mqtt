package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.WebhookConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WebhookConfigMapper extends BaseMapper<WebhookConfig> {

    List<WebhookConfig> findActiveByUserAndDevice(@Param("userId") Long userId, @Param("deviceId") Long deviceId);

    List<WebhookConfig> findActiveByUserId(@Param("userId") Long userId);
}
