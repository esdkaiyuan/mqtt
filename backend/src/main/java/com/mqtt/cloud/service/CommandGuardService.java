package com.mqtt.cloud.service;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.CommandProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

/**
 * 命令下发的标识符级权限守卫（T-34 / G-4-1）。
 * <p>
 * 承担角色闸门之下的**第二层**细分：角色闸门在 {@code SecurityConfig} 里把 VIEWER 整体挡在门外，
 * 本类处理「有权限的角色之间，某个命令能不能下发」：
 * <ol>
 *   <li><b>全局危险命令黑名单</b>（{@code app.command.denied-identifiers}）—— 默认含
 *       {@code factory_reset}。恢复出厂不可逆，不该因为「有下发权限」就直接放行；
 *       确需执行时由角色配置显式豁免，而不是全局放开。</li>
 *   <li><b>角色 → 命令类型矩阵</b>（{@code app.command.role-permissions}）—— 缺省即不限，
 *       这样新增角色不必改配置就能用。</li>
 * </ol>
 * <p>
 * <b>只作用于外部来源</b>（{@code CONSOLE} / {@code OPEN_API}）：规则、OTA、场景、点表下发是
 * 系统内部触发的，其 {@code operator} 只是「谁配置的这条自动化」，不代表现在有人手工操作 ——
 * 若对它们套「当前登录角色」的判定，会出现「VIEWER 配了一条规则，规则却再也执行不了」的反直觉故障。
 * 但这些来源仍**计入调用配额**（见 {@link CommandQuotaService}），因为规则风暴同样需要保护。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommandGuardService {

    /** 外部来源：控制台与开放接口，受角色 / 标识符权限约束。 */
    private static final Set<String> EXTERNAL_SOURCES =
            Set.of(DeviceCommandService.SOURCE_CONSOLE, DeviceCommandService.SOURCE_OPEN_API);

    private final CommandProperties properties;

    /**
     * 命令下发前的权限判定；不允许时抛 {@code 6296}（标识符级）/ {@code 6297}（类型级）。
     * <p>
     * 内部来源直接返回 —— 见类注释「只作用于外部来源」的说明。
     *
     * @param source      下发来源
     * @param commandType 命令类型（{@code property_set} / {@code service}）
     * @param identifier  属性标识符或服务标识符，可为 {@code null}
     */
    public void checkPermission(String source, String commandType, String identifier) {
        if (source == null || !EXTERNAL_SOURCES.contains(source)) {
            return;
        }
        String role = currentRole();
        CommandProperties.RolePermission permission =
                properties.getRolePermissions().get(role);

        // 1) 全局危险标识符：角色可豁免
        String normalized = normalize(identifier);
        if (normalized != null && isDenied(normalized) && !isExempt(permission, normalized)) {
            log.warn("命令下发被拒：危险标识符 role={}, identifier={}", role, identifier);
            throw new BusinessException(ResultCode.COMMAND_IDENTIFIER_DENIED,
                    "当前角色无权下发该命令：" + identifier);
        }

        // 2) 角色 → 命令类型矩阵：缺省不限
        if (permission == null || permission.getCommandTypes() == null
                || permission.getCommandTypes().isEmpty()) {
            return;
        }
        String type = normalize(commandType);
        if (type != null && !permission.getCommandTypes().stream().anyMatch(t -> type.equals(normalize(t)))) {
            log.warn("命令下发被拒：类型不允许 role={}, commandType={}", role, commandType);
            throw new BusinessException(ResultCode.COMMAND_TYPE_DENIED,
                    "当前角色无权下发该类型的命令：" + commandType);
        }
    }

    /** 标识符是否命中全局黑名单。 */
    public boolean isDenied(String identifier) {
        String normalized = normalize(identifier);
        if (normalized == null || properties.getDeniedIdentifiers() == null) {
            return false;
        }
        return properties.getDeniedIdentifiers().stream()
                .anyMatch(item -> normalized.equals(normalize(item)));
    }

    private boolean isExempt(CommandProperties.RolePermission permission, String normalized) {
        return permission != null && permission.getAllowedIdentifiers() != null
                && permission.getAllowedIdentifiers().stream()
                .anyMatch(item -> normalized.equals(normalize(item)));
    }

    /**
     * 取当前登录角色；无登录上下文（内部调用、开放接口走 API Key）时返回 {@code null}。
     * <p>
     * 返回 {@code null} 表示「不按角色判定」，而不是判定为某默认角色 ——
     * 这样内部调用与 API Key 调用不会因为缺少角色而被误拒。
     */
    private String currentRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            if (value != null && value.startsWith("ROLE_")) {
                return value.substring("ROLE_".length());
            }
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}