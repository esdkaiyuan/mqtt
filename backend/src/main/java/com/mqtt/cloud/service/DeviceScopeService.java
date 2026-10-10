package com.mqtt.cloud.service;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.service.scope.DeviceMemberships;
import com.mqtt.cloud.service.scope.RuleScope;

import java.util.List;

/**
 * 设备作用域解析服务（T-28 设计文档 §4.3 / §4.4）。
 * <p>
 * 提供两个方向的解析，各服务一条热路径：
 * <ul>
 *   <li><b>设备 → 隶属</b>（{@link #memberships}）：推评估每次面对一台设备却要判定全部规则，按设备解析一次复用；</li>
 *   <li><b>作用域 → 设备集合</b>（{@link #deviceIds}）：离线巡检每次面对一条规则却要筛全部设备。</li>
 * </ul>
 * 边界约定（ArchUnit 规则 4）：本接口不得依赖 {@code mapper}，只依赖其它服务接口；分组 / 标签归属校验
 * 复用 {@link DeviceGroupService#getOwned} / {@link DeviceTagService#getOwned}。
 * <p>
 * {@code DEVICE} 档的设备归属校验由调用方沿用既有 {@code DeviceService} 口径（越权 {@code DEVICE_NOT_OWNED}），
 * 本服务只负责作用域结构校验与分组 / 标签归属，避免在本服务内再引入设备域依赖。
 */
public interface DeviceScopeService {

    /** 解析一台设备的分组（含全部祖先）与标签隶属集合；{@code deviceId} 为空时返回空集合。 */
    DeviceMemberships memberships(Long userId, Long deviceId);

    /**
     * 解析作用域命中的设备 ID 集合（去重）。
     * <p>
     * {@code ALL} 档返回<b>空集</b>表示「不限设备」（由调用方的全量分支处理，不物化全部设备 ID）；
     * {@code DEVICE} 档返回单元素；{@code GROUP} 档按选中分组展开后代分组；{@code TAG} 档取关联设备。
     */
    List<Long> deviceIds(Long userId, RuleScope scope);

    /**
     * 由持久化字段装配作用域值对象：{@code groupIdsJson} / {@code tagIdsJson} 为库中 JSON 文本，
     * 解析统一走 {@code ScopeJson}；档位为空时按 {@code deviceId} 是否为空推断为 {@code DEVICE} / {@code ALL}。
     * <p>
     * 供三条评估路径把实体读成作用域，避免各写一份解析；对未知档位不抛异常，交由匹配方按不命中降级。
     */
    RuleScope toScope(String scopeType, Long deviceId, String groupIdsJson, String tagIdsJson);

    /**
     * 校验并规范化作用域：档位合法性、档位与字段一致性、分组 / 标签归属与数量上限、缺省档位推断。
     *
     * @param userId      归属用户
     * @param scope       待校验作用域；档位为空时按 {@code deviceId} 是否为空推断为 {@code DEVICE} / {@code ALL}
     * @param invalidCode 非法时抛出的业务码（告警 {@code 6268} / 规则 {@code 6269}）
     * @return 规范化后的作用域（去重、清除非本档字段）
     */
    RuleScope validateAndNormalize(Long userId, RuleScope scope, ResultCode invalidCode);
}