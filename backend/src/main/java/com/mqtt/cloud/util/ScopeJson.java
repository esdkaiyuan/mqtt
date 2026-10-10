package com.mqtt.cloud.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;

/**
 * 作用域分组 / 标签 ID 列表的 JSON 文本编解码（T-28 设计文档 §4.2）。
 * <p>
 * 规则与告警两侧的 {@code group_ids} / {@code tag_ids} 均为 JSON 数组文本，解析与序列化统一走本类，
 * 避免评估层与服务层各写一份而漂移。
 * <p>
 * 读路径（{@link #toIdList(String)}）对空值与非法文本一律降级为空列表并记 WARN —— 库中脏数据
 * 不得抛异常击穿评估与巡检链路。写路径（{@link #write(List)}）在空集合时返回 {@code null}，
 * 使「非该档」的列保持 NULL。
 */
@Slf4j
@Component
public class ScopeJson {

    private final ObjectMapper objectMapper;

    public ScopeJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 解析 ID 列表：空值 / 非法文本返回空列表；去重并丢弃 {@code null} 项，保持出现顺序。 */
    public List<Long> toIdList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Long> ids = objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            return ids.stream().filter(Objects::nonNull).distinct().toList();
        } catch (Exception e) {
            log.warn("作用域 ID 列表解析失败，按空集合处理: json={}", json, e);
            return List.of();
        }
    }

    /** 序列化 ID 列表：空集合返回 {@code null}；去重并丢弃 {@code null} 项，保持出现顺序。 */
    public String write(List<Long> ids) {
        if (ids == null) {
            return null;
        }
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(distinct);
    }
}