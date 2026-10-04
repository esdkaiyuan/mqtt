package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.entity.SceneDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 场景定义 Mapper（T-23 设计文档 §6.2）。
 * <p>
 * 逻辑删除由全局配置处理（{@code deleted=0} 自动过滤）；自定义查询按需显式声明。
 */
@Mapper
public interface SceneDefinitionMapper extends BaseMapper<SceneDefinition> {

    /** 某用户的启用场景（场景缓存加载用）。 */
    List<SceneDefinition> selectEnabledByUser(@Param("userId") Long userId);

    /** 场景分页列表：按归属用户过滤，可选触发源 / 启用态 / 关键字（名称或描述模糊）。 */
    IPage<SceneDefinition> pageByUser(Page<SceneDefinition> page,
                                      @Param("userId") Long userId,
                                      @Param("triggerType") String triggerType,
                                      @Param("enabled") Boolean enabled,
                                      @Param("keyword") String keyword);

    /**
     * 同用户重名校验：未删除场景数。
     *
     * @param excludeId 排除的场景 ID（更新时排除自身），可空
     */
    int countByUserAndName(@Param("userId") Long userId,
                           @Param("name") String name,
                           @Param("excludeId") Long excludeId);

    /** 某用户当前场景数（上限校验用）。 */
    int countByUser(@Param("userId") Long userId);

    /** 回填「最近触发」展示字段（自动触发路径调用；不刷新 updated_at）。 */
    int touchLastTriggered(@Param("id") Long id, @Param("triggeredAt") LocalDateTime triggeredAt);

    /**
     * 定时触发的**分钟去重**：仅当 {@code last_triggered_at} 早于本分钟起点时才回填。
     * <p>
     * 返回受影响行数；{@code 0} 表示本分钟已被其他副本 / 其他巡检轮次触发，调用方据此跳过。
     */
    int touchLastTriggeredOnce(@Param("id") Long id,
                               @Param("triggeredAt") LocalDateTime triggeredAt,
                               @Param("minuteFloor") LocalDateTime minuteFloor);

    /** 全部启用的定时场景（{@code trigger_type='TIMER'}）。 */
    List<SceneDefinition> selectEnabledTimers();
}