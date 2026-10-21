package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.HmiView;
import org.apache.ibatis.annotations.Mapper;

/**
 * 快速上位机 Mapper（T-38）：纯单表 CRUD，无自定义 SQL。
 */
@Mapper
public interface HmiViewMapper extends BaseMapper<HmiView> {
}
