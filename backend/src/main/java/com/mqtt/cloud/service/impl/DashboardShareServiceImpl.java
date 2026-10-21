package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.DashboardProperties;
import com.mqtt.cloud.dto.request.DashboardShareRequest;
import com.mqtt.cloud.dto.response.DashboardConfig;
import com.mqtt.cloud.dto.response.DashboardSharePublicVO;
import com.mqtt.cloud.dto.response.DashboardShareVO;
import com.mqtt.cloud.entity.Dashboard;
import com.mqtt.cloud.entity.DashboardShare;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.mapper.DashboardMapper;
import com.mqtt.cloud.mapper.DashboardShareMapper;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.DashboardShareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 看板分享实现（T-46）。
 * <p>
 * 创建 / 更新时校验看板归属；公开读取只返回分享创建者名下的看板与设备名。
 * 令牌使用 256 位随机数，数据库只保存 SHA-256 哈希，明文仅在创建 / 轮换时返回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardShareServiceImpl implements DashboardShareService {

    private static final int MAX_NAME_LENGTH = 64;
    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_PREVIEW_LENGTH = 8;
    private static final int DEFAULT_INTERVAL_SECONDS = 15;
    private static final int FALLBACK_MAX_SHARES = 20;
    private static final int FALLBACK_MAX_SHARE_DASHBOARDS = 10;
    private static final int FALLBACK_MIN_INTERVAL = 5;
    private static final int FALLBACK_MAX_INTERVAL = 3600;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DashboardShareMapper shareMapper;
    private final DashboardMapper dashboardMapper;
    private final DeviceMapper deviceMapper;
    private final DashboardProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public List<DashboardShareVO> list(Long userId) {
        List<DashboardShare> rows = shareMapper.selectList(new LambdaQueryWrapper<DashboardShare>()
                .eq(DashboardShare::getUserId, userId)
                .orderByDesc(DashboardShare::getUpdatedAt)
                .orderByDesc(DashboardShare::getId));
        List<DashboardShareVO> result = new ArrayList<>(rows.size());
        for (DashboardShare row : rows) {
            result.add(toVO(row, null));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DashboardShareVO create(Long userId, DashboardShareRequest request) {
        int maxShares = maxShares();
        Long existing = shareMapper.selectCount(new LambdaQueryWrapper<DashboardShare>()
                .eq(DashboardShare::getUserId, userId));
        if (existing != null && existing >= maxShares) {
            throw new BusinessException(ResultCode.DASHBOARD_SHARE_LIMIT_EXCEEDED,
                    "看板分享数量已达上限 " + maxShares);
        }
        String name = normalizeName(request);
        List<Long> dashboardIds = validateDashboards(userId, request);
        int interval = normalizeInterval(request);

        TokenPair token = generateToken();
        DashboardShare share = new DashboardShare();
        share.setUserId(userId);
        share.setName(name);
        share.setTokenHash(token.hash());
        share.setTokenPreview(token.preview());
        share.setDashboardIds(writeIds(dashboardIds));
        share.setIntervalSeconds(interval);
        share.setEnabled(request.getEnabled() == null || request.getEnabled() ? 1 : 0);
        shareMapper.insert(share);
        return toVO(share, token.token());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DashboardShareVO update(Long userId, Long id, DashboardShareRequest request) {
        DashboardShare share = getOwned(userId, id);
        String name = normalizeName(request);
        List<Long> dashboardIds = validateDashboards(userId, request);
        int interval = normalizeInterval(request);

        share.setName(name);
        share.setDashboardIds(writeIds(dashboardIds));
        share.setIntervalSeconds(interval);
        if (request.getEnabled() != null) {
            share.setEnabled(request.getEnabled() ? 1 : 0);
        }
        shareMapper.updateById(share);
        return toVO(share, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        DashboardShare share = getOwned(userId, id);
        shareMapper.deleteById(share.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DashboardShareVO rotate(Long userId, Long id) {
        DashboardShare share = getOwned(userId, id);
        TokenPair token = generateToken();
        share.setTokenHash(token.hash());
        share.setTokenPreview(token.preview());
        shareMapper.updateById(share);
        return toVO(share, token.token());
    }

    @Override
    public DashboardSharePublicVO publicView(String token) {
        String value = token == null ? null : token.trim();
        if (value == null || value.isEmpty()) {
            throw notFound();
        }
        DashboardShare share = shareMapper.selectOne(new LambdaQueryWrapper<DashboardShare>()
                .eq(DashboardShare::getTokenHash, sha256(value))
                .last("LIMIT 1"));
        if (share == null || share.getEnabled() == null || share.getEnabled() != 1) {
            throw notFound();
        }
        List<Long> ids = readIds(share.getDashboardIds());
        if (ids.isEmpty()) {
            throw notFound();
        }
        Map<Long, Dashboard> owned = new LinkedHashMap<>();
        List<Dashboard> dashboards = dashboardMapper.selectBatchIds(ids);
        if (dashboards != null) {
            for (Dashboard dashboard : dashboards) {
                if (dashboard != null && share.getUserId().equals(dashboard.getUserId())) {
                    owned.put(dashboard.getId(), dashboard);
                }
            }
        }
        List<DashboardSharePublicVO.DashboardItem> items = new ArrayList<>();
        Set<Long> deviceIds = new LinkedHashSet<>();
        Map<Long, DashboardConfig> configs = new LinkedHashMap<>();
        for (Long id : ids) {
            Dashboard dashboard = owned.get(id);
            if (dashboard == null) {
                continue;
            }
            DashboardConfig config = parseConfig(dashboard.getConfig());
            configs.put(id, config);
            for (DashboardConfig.Panel panel : config.getPanels()) {
                if (panel.getDeviceIds() != null) {
                    deviceIds.addAll(panel.getDeviceIds());
                }
            }
        }
        Map<String, String> deviceNames = resolveDeviceNames(share.getUserId(), deviceIds);
        Set<Long> allowedDevices = deviceIds.stream()
                .filter(id -> deviceNames.containsKey(String.valueOf(id)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Long id : ids) {
            Dashboard dashboard = owned.get(id);
            if (dashboard == null) {
                continue;
            }
            DashboardConfig config = configs.get(id);
            for (DashboardConfig.Panel panel : config.getPanels()) {
                if (panel.getDeviceIds() != null) {
                    panel.setDeviceIds(panel.getDeviceIds().stream()
                            .filter(allowedDevices::contains)
                            .toList());
                }
            }
            DashboardSharePublicVO.DashboardItem item = new DashboardSharePublicVO.DashboardItem();
            item.setId(dashboard.getId());
            item.setName(dashboard.getName());
            item.setConfig(config);
            item.setUpdatedAt(dashboard.getUpdatedAt());
            items.add(item);
        }
        if (items.isEmpty()) {
            throw notFound();
        }
        DashboardSharePublicVO result = new DashboardSharePublicVO();
        result.setName(share.getName());
        result.setIntervalSeconds(share.getIntervalSeconds() == null
                ? DEFAULT_INTERVAL_SECONDS : share.getIntervalSeconds());
        result.setDashboards(items);
        result.setDeviceNames(deviceNames);
        return result;
    }

    // ---------- 校验 ----------

    private DashboardShare getOwned(Long userId, Long id) {
        if (id == null) {
            throw notFound();
        }
        DashboardShare share = shareMapper.selectById(id);
        if (share == null || !userId.equals(share.getUserId())) {
            throw notFound();
        }
        return share;
    }

    private String normalizeName(DashboardShareRequest request) {
        String name = request == null ? null : request.getName();
        String trimmed = name == null ? null : name.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            throw invalid("分享名称不能为空");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw invalid("分享名称不能超过 " + MAX_NAME_LENGTH + " 字符");
        }
        return trimmed;
    }

    private List<Long> validateDashboards(Long userId, DashboardShareRequest request) {
        if (request == null || request.getDashboardIds() == null) {
            throw invalid("分享至少需要一个看板");
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : request.getDashboardIds()) {
            if (id != null) {
                unique.add(id);
            }
        }
        int maxDashboards = maxShareDashboards();
        if (unique.isEmpty()) {
            throw invalid("分享至少需要一个看板");
        }
        if (unique.size() > maxDashboards) {
            throw invalid("单个分享最多包含 " + maxDashboards + " 个看板");
        }
        List<Dashboard> dashboards = dashboardMapper.selectBatchIds(unique);
        if (dashboards == null || dashboards.size() != unique.size()
                || dashboards.stream().anyMatch(item -> item == null || !userId.equals(item.getUserId()))) {
            throw invalid("看板不存在或无权分享");
        }
        return new ArrayList<>(unique);
    }

    private int normalizeInterval(DashboardShareRequest request) {
        int interval = request == null || request.getIntervalSeconds() == null
                ? DEFAULT_INTERVAL_SECONDS : request.getIntervalSeconds();
        int min = minInterval();
        int max = maxInterval();
        if (interval < min || interval > max) {
            throw invalid("轮播间隔需在 [" + min + ", " + max + "] 秒");
        }
        return interval;
    }

    // ---------- 令牌 ----------

    private static TokenPair generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new TokenPair(token, sha256(token),
                token.substring(0, Math.min(TOKEN_PREVIEW_LENGTH, token.length())));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    // ---------- 视图 ----------

    private DashboardShareVO toVO(DashboardShare share, String token) {
        DashboardShareVO vo = new DashboardShareVO();
        vo.setId(share.getId());
        vo.setName(share.getName());
        vo.setTokenPreview(share.getTokenPreview());
        vo.setDashboardIds(readIds(share.getDashboardIds()));
        vo.setIntervalSeconds(share.getIntervalSeconds());
        vo.setEnabled(share.getEnabled() != null && share.getEnabled() == 1);
        vo.setCreatedAt(share.getCreatedAt());
        vo.setUpdatedAt(share.getUpdatedAt());
        if (token != null) {
            vo.setToken(token);
            vo.setPath("/display/" + token);
        }
        return vo;
    }

    private Map<String, String> resolveDeviceNames(Long ownerId, Set<Long> deviceIds) {
        Map<String, String> names = new LinkedHashMap<>();
        if (deviceIds.isEmpty()) {
            return names;
        }
        List<Device> devices = deviceMapper.selectBatchIds(deviceIds);
        if (devices == null) {
            return names;
        }
        for (Device device : devices) {
            if (device == null || !ownerId.equals(device.getOwnerId())) {
                continue;
            }
            String name = device.getDeviceName();
            names.put(String.valueOf(device.getId()),
                    name == null || name.isBlank() ? device.getDeviceKey() : name);
        }
        return names;
    }

    private DashboardConfig parseConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return new DashboardConfig();
        }
        try {
            DashboardConfig config = objectMapper.readValue(raw, DashboardConfig.class);
            if (config == null) {
                return new DashboardConfig();
            }
            if (config.getPanels() == null) {
                config.setPanels(new ArrayList<>());
            }
            return config;
        } catch (Exception e) {
            log.warn("看板分享配置解析失败，按空面板处理: {}", e.getMessage());
            return new DashboardConfig();
        }
    }

    private String writeIds(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            throw invalid("看板列表序列化失败");
        }
    }

    private List<Long> readIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Long> ids = objectMapper.readValue(json, new TypeReference<>() {
            });
            return ids == null ? List.of() : ids;
        } catch (Exception e) {
            log.warn("看板分享 ID 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    // ---------- 上限 ----------

    private int maxShares() {
        return properties.getMaxShares() > 0 ? properties.getMaxShares() : FALLBACK_MAX_SHARES;
    }

    private int maxShareDashboards() {
        return properties.getMaxShareDashboards() > 0
                ? properties.getMaxShareDashboards() : FALLBACK_MAX_SHARE_DASHBOARDS;
    }

    private int minInterval() {
        return properties.getShareIntervalMinSeconds() > 0
                ? properties.getShareIntervalMinSeconds() : FALLBACK_MIN_INTERVAL;
    }

    private int maxInterval() {
        int max = properties.getShareIntervalMaxSeconds() > 0
                ? properties.getShareIntervalMaxSeconds() : FALLBACK_MAX_INTERVAL;
        return Math.max(max, minInterval());
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ResultCode.DASHBOARD_SHARE_INVALID, message);
    }

    private static BusinessException notFound() {
        return new BusinessException(ResultCode.DASHBOARD_SHARE_NOT_FOUND);
    }

    private record TokenPair(String token, String hash, String preview) {
    }
}
