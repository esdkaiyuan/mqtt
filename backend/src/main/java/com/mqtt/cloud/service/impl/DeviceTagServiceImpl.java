package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.DeviceQueryDTO;
import com.mqtt.cloud.dto.request.DeviceTagRequest;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceTag;
import com.mqtt.cloud.entity.DeviceTagRelation;
import com.mqtt.cloud.mapper.DeviceTagMapper;
import com.mqtt.cloud.mapper.DeviceTagRelationMapper;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.DeviceTagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 设备标签服务实现（T-18 设计文档 §7.2 / §8.5）。
 * <p>
 * 校验集中在本类：非法配置统一抛 {@code 6215}，标签不存在 {@code 6214}，越权 {@code 403}；
 * 删除标签先解除其全部设备关联再逻辑删除。所有写入方法开启事务。
 */
@Service
public class DeviceTagServiceImpl extends ServiceImpl<DeviceTagMapper, DeviceTag> implements DeviceTagService {

    /** 展示色：6 位十六进制（如 {@code #409EFF}）。 */
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final DeviceTagRelationMapper tagRelationMapper;
    private final DeviceService deviceService;

    public DeviceTagServiceImpl(DeviceTagRelationMapper tagRelationMapper, DeviceService deviceService) {
        this.tagRelationMapper = tagRelationMapper;
        this.deviceService = deviceService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceTag create(Long userId, DeviceTagRequest request) {
        String name = requireName(request);
        String color = requireColor(request);
        if (baseMapper.countByUserAndName(userId, name, null) > 0) {
            throw invalid("标签名称已存在");
        }

        DeviceTag tag = new DeviceTag();
        tag.setUserId(userId);
        tag.setName(name);
        tag.setColor(color);
        save(tag);
        return tag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceTag update(Long userId, Long id, DeviceTagRequest request) {
        DeviceTag tag = getOwned(userId, id);
        String name = requireName(request);
        String color = requireColor(request);
        if (baseMapper.countByUserAndName(userId, name, id) > 0) {
            throw invalid("标签名称已存在");
        }

        tag.setName(name);
        tag.setColor(color);
        updateById(tag);
        return tag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        getOwned(userId, id);
        // 标签为扁平维度，删除不阻塞：先物理解除全部关联，再逻辑删除标签本身
        tagRelationMapper.deleteByTagId(id);
        removeById(id);
    }

    @Override
    public List<DeviceTag> list(Long userId) {
        return baseMapper.selectByUser(userId);
    }

    @Override
    public DeviceTag getOwned(Long userId, Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.DEVICE_TAG_NOT_FOUND);
        }
        DeviceTag tag = getById(id);
        if (tag == null) {
            throw new BusinessException(ResultCode.DEVICE_TAG_NOT_FOUND);
        }
        if (!userId.equals(tag.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return tag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addDevices(Long userId, Long tagId, List<Long> deviceIds) {
        getOwned(userId, tagId);
        List<Long> ownedIds = requireOwnedDeviceIds(userId, deviceIds);
        if (ownedIds.isEmpty()) {
            return;
        }
        tagRelationMapper.insertIgnoreBatch(ownedIds, tagId, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeDevices(Long userId, Long tagId, List<Long> deviceIds) {
        getOwned(userId, tagId);
        List<Long> ownedIds = requireOwnedDeviceIds(userId, deviceIds);
        if (ownedIds.isEmpty()) {
            return;
        }
        tagRelationMapper.deleteByDeviceIdsAndTag(ownedIds, tagId);
    }

    @Override
    public IPage<Device> pageDevices(Long userId, Long tagId, long page, long size) {
        getOwned(userId, tagId);
        DeviceQueryDTO dto = new DeviceQueryDTO();
        dto.setOwnerId(userId);
        dto.setTagId(tagId);
        dto.setPageNum((int) page);
        dto.setPageSize((int) size);
        return deviceService.getDevices(userId, dto);
    }

    @Override
    public Map<Long, List<DeviceTag>> tagsByDeviceIds(Collection<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Map.of();
        }
        List<DeviceTagRelation> relations = tagRelationMapper.selectByDeviceIds(deviceIds);
        if (relations.isEmpty()) {
            return Map.of();
        }
        Set<Long> tagIds = relations.stream().map(DeviceTagRelation::getTagId).collect(Collectors.toSet());
        Map<Long, DeviceTag> byId = baseMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(DeviceTag::getId, t -> t, (a, b) -> a));
        Map<Long, List<DeviceTag>> result = new HashMap<>();
        for (DeviceTagRelation relation : relations) {
            DeviceTag tag = byId.get(relation.getTagId());
            if (tag != null) {
                result.computeIfAbsent(relation.getDeviceId(), k -> new ArrayList<>()).add(tag);
            }
        }
        return result;
    }

    @Override
    public List<Long> deviceIdsByTagIds(Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        return tagRelationMapper.selectDeviceIdsByTagIds(tagIds);
    }

    private String requireName(DeviceTagRequest request) {
        if (request == null) {
            throw invalid("请求体不能为空");
        }
        String name = trimToNull(request.getName());
        if (name == null) {
            throw invalid("标签名称不能为空");
        }
        if (name.length() > 64) {
            throw invalid("标签名称长度不能超过 64");
        }
        return name;
    }

    private String requireColor(DeviceTagRequest request) {
        String color = trimToNull(request.getColor());
        if (color != null && !COLOR_PATTERN.matcher(color).matches()) {
            throw invalid("标签颜色需为 #RRGGBB 格式");
        }
        return color;
    }

    /** 校验设备归属：不存在 / 已删除 {@code 2002}，非本人 {@code 2003}；返回去重后的本人设备 ID。 */
    private List<Long> requireOwnedDeviceIds(Long userId, List<Long> deviceIds) {
        List<Long> distinct = normalize(deviceIds);
        if (distinct.isEmpty()) {
            return List.of();
        }
        List<Device> devices = deviceService.listByIds(distinct);
        if (devices.size() != distinct.size()) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        for (Device device : devices) {
            if (!userId.equals(device.getOwnerId())) {
                throw new BusinessException(ResultCode.DEVICE_NOT_OWNED);
            }
        }
        return devices.stream().map(Device::getId).toList();
    }

    private List<Long> normalize(List<Long> deviceIds) {
        if (deviceIds == null) {
            return List.of();
        }
        return deviceIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ResultCode.DEVICE_TAG_INVALID, message);
    }
}