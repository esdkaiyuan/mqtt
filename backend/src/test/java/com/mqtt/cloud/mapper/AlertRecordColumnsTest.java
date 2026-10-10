package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mqtt.cloud.entity.AlertRecord;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守卫 AlertRecord 实体映射含 T-27 新增的升级与分派三列。
 * <p>
 * 背景：升级巡检按 {@code assignee_id} / {@code escalation_level} / {@code last_escalated_at} 读写，
 * 若实体漏映射任一列，条件更新与指派会静默失效（写入被 MP 跳过、候选查询恒空），
 * 而单元测试 mock 了 Mapper 无法发现。此测试在默认构建中运行，防止列映射再次漂移。
 * <p>
 * {@code assignee_id} 额外断言更新策略为 {@link FieldStrategy#ALWAYS}：解除指派需把该列显式置空，
 * 若沿用默认 {@code NOT_NULL} 策略，null 值会被 MP 跳过而无法解除。
 */
class AlertRecordColumnsTest {

    @BeforeEach
    void setUp() {
        // 纯单测无 MyBatis 上下文，需手动初始化实体的 TableInfo
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AlertRecord.class);
    }

    @Test
    void entity_should_map_escalation_columns() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(AlertRecord.class);
        assertThat(tableInfo).isNotNull();

        Map<String, String> columnToProperty = tableInfo.getFieldList().stream()
                .collect(Collectors.toMap(TableFieldInfo::getColumn, TableFieldInfo::getProperty));

        assertThat(columnToProperty)
                .containsEntry("assignee_id", "assigneeId")
                .containsEntry("escalation_level", "escalationLevel")
                .containsEntry("last_escalated_at", "lastEscalatedAt");
    }

    @Test
    void assignee_id_should_use_always_update_strategy() throws Exception {
        Field field = AlertRecord.class.getDeclaredField("assigneeId");
        TableField annotation = field.getAnnotation(TableField.class);

        assertThat(annotation).as("assigneeId 须挂 @TableField").isNotNull();
        assertThat(annotation.value()).isEqualTo("assignee_id");
        assertThat(annotation.updateStrategy())
                .as("解除指派需把 assignee_id 置空，必须用 ALWAYS 策略")
                .isEqualTo(FieldStrategy.ALWAYS);
    }
}