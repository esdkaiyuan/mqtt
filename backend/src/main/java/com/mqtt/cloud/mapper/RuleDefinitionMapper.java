package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.entity.RuleDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息规则定义 Mapper（T-19 设计文档 §6.2）。
 * <p>
 * 逻辑删除由全局配置处理（{@code deleted=0} 自动过滤）；自定义查询按需显式声明。
 */
@Mapper
public interface RuleDefinitionMapper extends BaseMapper<RuleDefinition> {

    /** 某用户的启用规则（规则缓存加载用）。 */
    List<RuleDefinition> selectEnabledByUser(@Param("userId") Long userId);

    /**
     * 规则分页列表：按归属用户过滤，可选来源 / 动作 / 启用态 / 关键字（名称或描述模糊）。
     */
    IPage<RuleDefinition> pageByUser(Page<RuleDefinition> page,
                                     @Param("userId") Long userId,
                                     @Param("sourceType") String sourceType,
                                     @Param("actionType") String actionType,
                                     @Param("enabled") Boolean enabled,
                                     @Param("keyword") String keyword);

    /**
     * 同用户重名校验：未删除规则数。
     *
     * @param excludeId 排除的规则 ID（更新时排除自身），可空
     */
    int countByUserAndName(@Param("userId") Long userId,
                           @Param("name") String name,
                           @Param("excludeId") Long excludeId);

    /** 某用户当前规则数（上限校验用）。 */
    int countByUser(@Param("userId") Long userId);

    /** 回填「最近触发」展示字段（由异步执行路径调用）。 */
    int touchLastTriggered(@Param("id") Long id, @Param("triggeredAt") LocalDateTime triggeredAt);
}