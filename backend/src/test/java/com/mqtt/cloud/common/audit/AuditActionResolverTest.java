package com.mqtt.cloud.common.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuditActionResolver} 单元测试（T-53）。
 * <p>
 * 审计动作是展示与检索口径，不是权限判断；这里锁定常见路径的解析结果，
 * 避免新增控制器后动作名悄悄退化成 CREATE / DELETE。
 */
class AuditActionResolverTest {

    @Test
    void deviceCommand_should_resolve_command_and_device_key() {
        AuditActionResolver.Resolved resolved =
                AuditActionResolver.resolve("POST", "/devices/sensor-01/commands");

        assertThat(resolved.action()).isEqualTo("COMMAND");
        assertThat(resolved.resourceType()).isEqualTo("DEVICE");
        assertThat(resolved.resourceId()).isEqualTo("sensor-01");
    }

    @Test
    void batchCommand_should_resolve_batch_resource_without_id() {
        AuditActionResolver.Resolved resolved =
                AuditActionResolver.resolve("POST", "/devices/batch/commands");

        assertThat(resolved.action()).isEqualTo("COMMAND");
        assertThat(resolved.resourceType()).isEqualTo("DEVICE_BATCH");
        assertThat(resolved.resourceId()).isNull();
    }

    @Test
    void deviceImport_and_thingModelImport_should_resolve_import() {
        AuditActionResolver.Resolved deviceImport =
                AuditActionResolver.resolve("POST", "/devices/import");
        assertThat(deviceImport.action()).isEqualTo("IMPORT");
        assertThat(deviceImport.resourceType()).isEqualTo("DEVICE");
        assertThat(deviceImport.resourceId()).isNull();

        AuditActionResolver.Resolved tslImport =
                AuditActionResolver.resolve("POST", "/products/7/thing-model/import");
        assertThat(tslImport.action()).isEqualTo("IMPORT");
        assertThat(tslImport.resourceType()).isEqualTo("PRODUCT");
        assertThat(tslImport.resourceId()).isEqualTo("7");
    }

    @Test
    void shadowWrite_should_not_be_mistaken_for_update() {
        AuditActionResolver.Resolved resolved =
                AuditActionResolver.resolve("PUT", "/devices/12/shadow/desired");

        assertThat(resolved.action()).isEqualTo("SHADOW_WRITE");
        assertThat(resolved.resourceType()).isEqualTo("DEVICE");
        assertThat(resolved.resourceId()).isEqualTo("12");
    }

    @Test
    void alertAck_andSceneRun_should_resolve_specific_actions() {
        assertThat(AuditActionResolver.resolve("POST", "/alerts/9/ack").action()).isEqualTo("ACK");
        assertThat(AuditActionResolver.resolve("POST", "/alerts/9/recover").action()).isEqualTo("RECOVER");
        assertThat(AuditActionResolver.resolve("POST", "/scenes/3/run").action()).isEqualTo("RUN");
        assertThat(AuditActionResolver.resolve("POST", "/scenes/3/test").action()).isEqualTo("TEST");
    }

    @Test
    void otaPause_should_resolve_ota_task_id() {
        AuditActionResolver.Resolved resolved =
                AuditActionResolver.resolve("POST", "/ota/tasks/15/pause");

        assertThat(resolved.action()).isEqualTo("PAUSE");
        assertThat(resolved.resourceType()).isEqualTo("OTA_TASK");
        assertThat(resolved.resourceId()).isEqualTo("15");
    }

    @Test
    void deleteScene_should_fall_back_to_delete() {
        AuditActionResolver.Resolved resolved =
                AuditActionResolver.resolve("DELETE", "/scenes/3");

        assertThat(resolved.action()).isEqualTo("DELETE");
        assertThat(resolved.resourceType()).isEqualTo("SCENE");
        assertThat(resolved.resourceId()).isEqualTo("3");
    }

    @Test
    void authPaths_should_not_treat_static_segment_as_resource_id() {
        AuditActionResolver.Resolved login =
                AuditActionResolver.resolve("POST", "/auth/login");

        assertThat(login.action()).isEqualTo("LOGIN");
        assertThat(login.resourceType()).isEqualTo("AUTH");
        assertThat(login.resourceId()).isNull();
    }

    @Test
    void trailingSlash_should_be_ignored() {
        AuditActionResolver.Resolved resolved =
                AuditActionResolver.resolve("POST", "/device-registration/activate/");

        assertThat(resolved.action()).isEqualTo("ACTIVATE");
        assertThat(resolved.resourceType()).isEqualTo("DEVICE_REGISTRATION");
        assertThat(resolved.resourceId()).isNull();
    }
}