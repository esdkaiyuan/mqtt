package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.DeviceNodeType;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.exception.GlobalExceptionHandler;
import com.mqtt.cloud.config.DeviceTopologyProperties;
import com.mqtt.cloud.dto.request.CreateSubDeviceDTO;
import com.mqtt.cloud.dto.request.DeviceQueryDTO;
import com.mqtt.cloud.dto.response.DeviceCreatedDTO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.DeviceTopologyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 设备拓扑接口单测（T-26 设计文档 §10.1 / §14.1）。
 * <p>
 * 覆盖六个端点的方法、路径、参数校验与返回体：建子设备、挂载、解绑、列子设备、查父网关、变更角色。
 * 另以 Stand-alone {@link MockMvc} 挂载 {@link GlobalExceptionHandler}，断言 {@code enabled=false}
 * 时端点返回 {@code 404}、请求体校验失败返回 {@code 400}。
 * <p>
 * 方法命名遵循 {@code x_should_y_when_z} 约定。
 */
@ExtendWith(MockitoExtension.class)
class DeviceTopologyControllerTest {

    private static final Long USER_ID = 10L;
    private static final Long GATEWAY_ID = 20L;
    private static final Long SUB_ID = 30L;

    @Mock
    private DeviceTopologyService deviceTopologyService;

    @Mock
    private DeviceService deviceService;

    @Mock
    private DeviceTopologyProperties topologyProperties;

    @InjectMocks
    private DeviceTopologyController controller;

    @BeforeEach
    void enableTopologyByDefault() {
        // 多数用例关注业务转发，默认放行开关；关闭场景在对应用例内覆写
        lenient().when(topologyProperties.isEnabled()).thenReturn(true);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createSubDevice_should_delegate_after_ownership_check() throws Exception {
        authenticate();
        when(deviceService.getById(GATEWAY_ID)).thenReturn(ownedDevice(GATEWAY_ID));
        DeviceCreatedDTO created = new DeviceCreatedDTO();
        created.setId(SUB_ID);
        created.setDeviceKey("sub-key");
        when(deviceService.createSubDevice(eq(GATEWAY_ID), any(CreateSubDeviceDTO.class))).thenReturn(created);

        mockMvc().perform(post("/devices/" + GATEWAY_ID + "/sub-devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceName\":\"子设备A\",\"deviceKey\":\"sub-key\","
                                + "\"deviceType\":\"sensor\",\"topic\":\"device/sub-key\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(SUB_ID.intValue()))
                .andExpect(jsonPath("$.data.deviceKey").value("sub-key"));

        verify(deviceService).createSubDevice(eq(GATEWAY_ID), any(CreateSubDeviceDTO.class));
    }

    @Test
    void attachSubDevice_should_delegate() throws Exception {
        authenticate();
        when(deviceService.getById(GATEWAY_ID)).thenReturn(ownedDevice(GATEWAY_ID));
        when(deviceService.getById(SUB_ID)).thenReturn(ownedDevice(SUB_ID));
        Device attached = ownedDevice(SUB_ID);
        attached.setNodeType(DeviceNodeType.SUB);
        attached.setParentId(GATEWAY_ID);
        when(deviceTopologyService.attach(GATEWAY_ID, SUB_ID)).thenReturn(attached);

        mockMvc().perform(post("/devices/" + GATEWAY_ID + "/sub-devices/" + SUB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nodeType").value(DeviceNodeType.SUB))
                .andExpect(jsonPath("$.data.parentId").value(GATEWAY_ID.intValue()));

        verify(deviceTopologyService).attach(GATEWAY_ID, SUB_ID);
    }

    @Test
    void detachSubDevice_should_delegate() throws Exception {
        authenticate();
        when(deviceService.getById(GATEWAY_ID)).thenReturn(ownedDevice(GATEWAY_ID));
        when(deviceService.getById(SUB_ID)).thenReturn(ownedDevice(SUB_ID));

        mockMvc().perform(delete("/devices/" + GATEWAY_ID + "/sub-devices/" + SUB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(deviceTopologyService).detach(GATEWAY_ID, SUB_ID);
    }

    @Test
    void listSubDevices_should_delegate_with_paging() throws Exception {
        authenticate();
        when(deviceService.getById(GATEWAY_ID)).thenReturn(ownedDevice(GATEWAY_ID));
        Page<Device> page = new Page<>(1, 20);
        page.setRecords(List.of(ownedDevice(SUB_ID)));
        when(deviceTopologyService.listSubDevices(eq(GATEWAY_ID), any())).thenReturn(page);

        mockMvc().perform(get("/devices/" + GATEWAY_ID + "/sub-devices").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].id").value(SUB_ID.intValue()));

        ArgumentCaptor<DeviceQueryDTO> captor = ArgumentCaptor.forClass(DeviceQueryDTO.class);
        verify(deviceTopologyService).listSubDevices(eq(GATEWAY_ID), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void listSubDevices_should_allow_admin_to_read_foreign_gateway() throws Exception {
        authenticateAsAdmin();
        Device foreign = new Device();
        foreign.setId(GATEWAY_ID);
        foreign.setOwnerId(999L);
        foreign.setDeleted(0);
        when(deviceService.getById(GATEWAY_ID)).thenReturn(foreign);
        when(deviceTopologyService.listSubDevices(eq(GATEWAY_ID), any())).thenReturn(new Page<>(1, 10));

        mockMvc().perform(get("/devices/" + GATEWAY_ID + "/sub-devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getParent_should_delegate() throws Exception {
        authenticate();
        when(deviceService.getById(SUB_ID)).thenReturn(ownedDevice(SUB_ID));
        Device gateway = ownedDevice(GATEWAY_ID);
        gateway.setNodeType(DeviceNodeType.GATEWAY);
        when(deviceTopologyService.findParent(SUB_ID)).thenReturn(gateway);

        mockMvc().perform(get("/devices/" + SUB_ID + "/parent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(GATEWAY_ID.intValue()));

        verify(deviceTopologyService).findParent(SUB_ID);
    }

    @Test
    void changeNodeType_should_delegate() throws Exception {
        authenticate();
        when(deviceService.getById(GATEWAY_ID)).thenReturn(ownedDevice(GATEWAY_ID));
        Device gateway = ownedDevice(GATEWAY_ID);
        gateway.setNodeType(DeviceNodeType.GATEWAY);
        when(deviceTopologyService.changeNodeType(GATEWAY_ID, DeviceNodeType.GATEWAY)).thenReturn(gateway);

        mockMvc().perform(put("/devices/" + GATEWAY_ID + "/node-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nodeType\":\"GATEWAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nodeType").value(DeviceNodeType.GATEWAY));

        verify(deviceTopologyService).changeNodeType(GATEWAY_ID, DeviceNodeType.GATEWAY);
    }

    @Test
    void changeNodeType_should_reject_missing_node_type() throws Exception {
        authenticate();
        when(deviceService.getById(GATEWAY_ID)).thenReturn(ownedDevice(GATEWAY_ID));

        mockMvc().perform(put("/devices/" + GATEWAY_ID + "/node-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResultCode.DEVICE_TOPOLOGY_INVALID.getCode()));

        verify(deviceTopologyService, never()).changeNodeType(any(), any());
    }

    @Test
    void createSubDevice_should_reject_body_without_device_key() throws Exception {
        authenticate();

        mockMvc().perform(post("/devices/" + GATEWAY_ID + "/sub-devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceName\":\"子设备A\",\"deviceType\":\"sensor\",\"topic\":\"device/x\"}"))
                .andExpect(status().isBadRequest());

        verify(deviceService, never()).createSubDevice(any(), any());
    }

    @Test
    void attachSubDevice_should_reject_foreign_device() {
        authenticate();
        when(deviceService.getById(GATEWAY_ID)).thenReturn(ownedDevice(GATEWAY_ID));
        Device foreign = new Device();
        foreign.setId(SUB_ID);
        foreign.setOwnerId(999L);
        foreign.setDeleted(0);
        when(deviceService.getById(SUB_ID)).thenReturn(foreign);

        assertThatThrownBy(() -> controller.attachSubDevice(GATEWAY_ID, SUB_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.DEVICE_NOT_OWNED.getCode());

        verify(deviceTopologyService, never()).attach(any(), any());
    }

    @Test
    void topology_endpoints_should_return_404_when_disabled() throws Exception {
        authenticate();
        when(topologyProperties.isEnabled()).thenReturn(false);

        mockMvc().perform(get("/devices/" + GATEWAY_ID + "/sub-devices"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResultCode.NOT_FOUND.getCode()));

        verifyNoInteractions(deviceTopologyService, deviceService);
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Device ownedDevice(Long id) {
        Device device = new Device();
        device.setId(id);
        device.setOwnerId(USER_ID);
        device.setDeleted(0);
        return device;
    }

    private void authenticate() {
        authenticateWithRole("USER");
    }

    private void authenticateAsAdmin() {
        authenticateWithRole("ADMIN");
    }

    private void authenticateWithRole(String role) {
        UserPrincipal principal = new UserPrincipal(USER_ID, "user-" + USER_ID, role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
