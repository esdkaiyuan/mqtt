package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.ApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKey> {

    List<ApiKey> findActiveByUserId(@Param("userId") Long userId);

    ApiKey findByKeyValue(@Param("keyValue") String keyValue);

    List<ApiKey> findExpiredKeys();
}
