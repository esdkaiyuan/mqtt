package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.AlertEscalationPolicy;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警升级策略 Mapper（T-27 设计文档 §6.4）。
 * <p>
 * 仅需基础 CRUD 与逻辑删除；升级巡检的候选策略查询在服务层用条件构造器表达，
 * 故不额外声明自定义语句。
 */
@Mapper
public interface AlertEscalationPolicyMapper extends BaseMapper<AlertEscalationPolicy> {
}