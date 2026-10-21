package com.mqtt.cloud.service;

import com.mqtt.cloud.dto.request.HmiSaveDTO;
import com.mqtt.cloud.dto.response.HmiDetailVO;
import com.mqtt.cloud.dto.response.HmiDraftVO;
import com.mqtt.cloud.dto.response.HmiSummaryVO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;

import java.util.List;

/**
 * 快速上位机（T-38）。
 * <p>
 * 仅管理「本人 + 单设备」的 Web 组态：列表 / 创建 / 详情 / 更新 / 删除，
 * 以及按物模型生成不落库的草稿。设备归属由控制层校验后传入，本接口不重复。
 * <p>
 * 边界约定（ArchUnit 规则 4）：本接口不得依赖 {@code mapper}。
 */
public interface HmiViewService {

    List<HmiSummaryVO> list(Long userId);

    HmiDetailVO create(Long userId, Device device, Product product, HmiSaveDTO dto);

    HmiDetailVO detail(Long userId, Long id);

    HmiDetailVO update(Long userId, Long id, Device device, Product product, HmiSaveDTO dto);

    void remove(Long userId, Long id);

    /** 按物模型生成控件草稿；未建模时至少返回「最近事件」并带降级说明。 */
    HmiDraftVO draft(Device device, Product product);
}
