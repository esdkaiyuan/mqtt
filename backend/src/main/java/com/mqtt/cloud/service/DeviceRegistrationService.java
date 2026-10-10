package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.dto.request.ActivateDeviceDTO;
import com.mqtt.cloud.dto.request.BatchRegisterDeviceDTO;
import com.mqtt.cloud.dto.response.BatchRegisterResultDTO;
import com.mqtt.cloud.entity.DeviceRegBatch;

/**
 * 设备批量注册与激活服务（T-30）。
 * <p>
 * 补齐商用 IoT 平台的标准接入流程：
 * <pre>
 *   批量注册（生成 deviceKey 与一次性凭据）
 *      → 下发凭据到设备（烧录 / 下发指令）
 *      → 设备首次连接时消费激活码完成激活
 * </pre>
 * 单设备创建 {@code POST /devices} 保持不变，两条入口共用同一套
 * deviceKey 唯一性校验与凭据签发逻辑。
 */
public interface DeviceRegistrationService {

    /**
     * 批量注册设备：按模板生成 deviceKey，逐台签发一机一密凭据并落一条批次记录。
     * <p>
     * 逐台独立事务，单台失败不阻断整批；失败明细随响应返回。
     *
     * @return 含一次性凭据明文的批次结果
     */
    BatchRegisterResultDTO batchRegister(Long userId, BatchRegisterDeviceDTO dto);

    /**
     * 设备激活：消费激活码并置 {@code enabled=1}、写入 {@code activated_at}。
     * <p>
     * 激活码非法 / 已被使用 / 已过期分别返回 {@code 6273} / {@code 6273} / {@code 6274}。
     * 重复激活同一设备返回成功（幂等）。
     */
    void activate(ActivateDeviceDTO dto);

    /** 查询注册批次分页，仅批次归属用户可见。 */
    IPage<DeviceRegBatch> pageBatches(Long userId, int page, int size);

    /** 查询批次详情，仅批次归属用户可见；非归属返回 {@code 6270}。 */
    DeviceRegBatch getBatch(Long userId, String batchNo);
}
