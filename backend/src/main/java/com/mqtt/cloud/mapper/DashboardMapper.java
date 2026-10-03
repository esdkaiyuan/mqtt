package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.Dashboard;
import org.apache.ibatis.annotations.Mapper;

/**
 * 可保存看板 Mapper（T-21 设计文档 §7.1）。
 * <p>
 * 纯单表 CRUD，直接继承 {@code BaseMapper}，无自定义 SQL。
 */
@Mapper
public interface DashboardMapper extends BaseMapper<Dashboard> {
}
