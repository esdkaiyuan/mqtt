package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.AlertRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 告警规则 Mapper（T-17 设计文档 §6.2）。
 * <p>
 * 逻辑删除由全局配置处理（{@code deleted=0} 自动过滤）；自定义查询按需显式声明。
 */
@Mapper
public interface AlertRuleMapper extends BaseMapper<AlertRule> {

    /**
     * 按用户 + 来源查启用规则（控制台列表 / 批量展示）。
     *
     * @param sourceType 可空，空则不过滤来源
     */
    List<AlertRule> selectEnabledByUser(@Param("userId") Long userId,
                                        @Param("sourceType") String sourceType);

    /**
     * 查某设备适用的启用规则：作用域为「全部设备」（{@code device_id IS NULL}）或正是该设备。
     * 用于阈值 / 事件「推」评估。
     */
    List<AlertRule> selectEnabledForDevice(@Param("userId") Long userId,
                                           @Param("deviceId") Long deviceId,
                                           @Param("sourceType") String sourceType);

    /**
     * 跨用户按来源取启用规则（离线巡检用），按 id 升序分批。
     */
    List<AlertRule> selectEnabledBySource(@Param("sourceType") String sourceType,
                                          @Param("limit") int limit);
}
