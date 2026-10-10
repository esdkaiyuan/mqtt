package com.mqtt.cloud.collect;

import java.util.List;

/**
 * 点位采集器 SPI（T-31）
 * <p>
 * 这是路线 A 与路线 B 唯一的抽象基座：<b>二者的差异被压缩到「谁来调用这个接口」，
 * 而「怎么把一个点位采回来」在两条路线上完全一致</b>。
 * <ul>
 *   <li>路线 B（平台侧 Modbus 主站）：{@link com.mqtt.cloud.service.impl.ModbusPointDispatchScheduler}
 *       在轮询到期时调用实现类，主动出网读取现场设备；</li>
 *   <li>路线 A（边缘驱动）：平台<b>不</b>调用本接口 —— 点表下发给网关后由网关自行采集，
 *       回执走既有的 {@code IngestPipeline}，从平台视角看与 MQTT 上报无异。</li>
 * </ul>
 * <p>
 * <b>为什么接口里不能出现 Modbus 字样</b>：{@link #driverId()} 是唯一的协议绑定点，
 * 实现类通过 {@code @Component} 注册到 Spring 容器。接口层一旦出现协议相关类型
 * （如寄存器、功能码、Modbus 主站句柄），后续接 OPC-UA 就必须改动接口契约，
 * 所有已实现该接口的驱动都要跟着改。把协议知识挡在实现类内部，是这个 SPI 能长期扩展的前提。
 *
 * @see CollectRequest 采集输入
 * @see CollectResult 采集输出（成功与失败明细都必须返回，不允许静默吞掉失败）
 */
public interface PointCollector {

    /**
     * 驱动标识，与 {@code product.driver_id} 对应，由平台据此选择本实现。
     * <p>
     * 约定为协议 + 传输方式的小写下划线形式（如 {@code modbus-tcp}），大小写不敏感匹配。
     *
     * @return 驱动标识，不可为 {@code null} 或空白
     */
    String driverId();

    /**
     * 执行一次采集。
     * <p>
     * <b>实现约定</b>：
     * <ol>
     *   <li><b>不抛异常</b>。协议错误（连接超时、寄存器不存在、响应校验失败）必须转为
     *       {@link CollectResult} 中的失败条目返回——采集是轮询作业，抛出异常会导致
     *       整个调度轮的其余设备全部丢失，这正是「单个点位配错导致全设备采集停摆」的成因；</li>
     *   <li><b>资源必须在 finally 关闭</b>。协议栈的连接是有状态资源，泄漏会累积到
     *       耗尽文件描述符，届时表现为整机采集失效；</li>
     *   <li>失败条目必须填写 {@link PointValue#errorMessage}，供运维定位到具体点位与原因。</li>
     * </ol>
     *
     * @param request 采集输入，含目标点位、驱动配置与超时约束
     * @return 采集结果；点位无返回时返回空列表而非 {@code null}
     */
    List<PointValue> collect(CollectRequest request);
}
