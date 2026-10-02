package com.mqtt.cloud.service;

import com.mqtt.cloud.dto.response.ThingModelResponse;

/**
 * 物模型的读取、保存与校验入口。
 * <p>
 * 边界约定（ArchUnit 规则 4）：本接口不得依赖 {@code mapper}，持久化细节只出现在 {@code service.impl}；
 * 摄取解析链路（{@code ingest}）只依赖本接口，不触碰物模型的存储实现。
 */
public interface ThingModelService {

    /** 查询产品物模型；未建模时 {@code thingModel} 为 {@code null}、版本为 0。 */
    ThingModelResponse get(Long productId);

    /** 保存物模型：先校验，通过后版本号 +1 并失效本副本缓存。 */
    ThingModelResponse save(Long productId, String thingModelJson);

    /** 清空物模型：版本号 +1 并失效缓存；已为空时幂等空操作。 */
    ThingModelResponse clear(Long productId);

    /** 只校验不保存，返回全部错误路径。 */
    ThingModelValidationResult validate(Long productId, String thingModelJson);

    /** 导出 TSL 原文，文件名含 productKey 与版本号。 */
    ThingModelExport export(Long productId);

    /** 导入 TSL：等价于保存（同样校验、同样版本自增）。 */
    ThingModelResponse importModel(Long productId, String thingModelJson);

    /**
     * 解析链路专用：取产品的物模型投影，缓存优先、未命中回源。
     * <p>
     * 与 {@link #get(Long)} 不同，本方法在热路径上调用，<b>不抛业务异常</b>：
     * 产品不存在或物模型非法时返回 {@link ThingModelDefinition#EMPTY}。
     */
    ThingModelDefinition getForProduct(Long productId);

    /** 导出产物：文件名与 TSL 原文。 */
    record ThingModelExport(String fileName, String content) {
    }
}