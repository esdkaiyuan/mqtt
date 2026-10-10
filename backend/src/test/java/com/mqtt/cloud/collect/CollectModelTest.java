package com.mqtt.cloud.collect;

import com.mqtt.cloud.entity.DevicePointTable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 采集 SPI 数据载体用例（T-31 P1）。
 * <p>
 * 钉死两条契约，它们直接决定 A / B 两条路线的隔离是否成立：
 * <ol>
 *   <li>{@link CollectRequest} 必须对点位集合做防御性拷贝 —— 采集在专用线程池中异步执行，
 *       而点位列表来自 Mapper 查询；若直接持有可变列表，调度线程并发修改点位配置时
 *       会把 ConcurrentModificationException 抛进采集逻辑，表现为随机的采集失败；</li>
 *   <li>{@link CollectResult} 必须能区分「点位级失败」与「轮次级失败」——两者的处置动作
 *       完全不同（前者只记日志，后者触发设备离线告警）。若混同，一个地址配错的点位
 *       就会反复触发设备离线告警，把告警系统变成噪声源。</li>
 * </ol>
 */
class CollectModelTest {

    @Test
    void request_should_snapshot_mutable_point_list() {
        List<DevicePointTable> source = new ArrayList<>(List.of(point(1L)));
        CollectRequest request = new CollectRequest(source, "{}", Duration.ofSeconds(3), Duration.ofSeconds(3));

        // 调度线程在采集执行期间修改了原始列表
        source.add(point(2L));

        // 采集看到的必须仍是提交时的快照，不能是「碰巧还没炸」的结果
        assertThat(request.points()).hasSize(1);
    }

    @Test
    void request_should_tolerate_null_points() {
        CollectRequest request = new CollectRequest(null, null, Duration.ofSeconds(1), Duration.ofSeconds(1));
        assertThat(request.points()).isEmpty();
    }

    @Test
    void result_should_separate_successes_and_failures() {
        CollectResult result = new CollectResult(List.of(
                PointValue.ok(1L, 100L, "temp", "300", "30.0"),
                PointValue.failed(2L, 100L, "humidity", "地址越界")), true, null);

        assertThat(result.connected()).isTrue();
        assertThat(result.successes()).hasSize(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).pointKey()).isEqualTo("humidity");
    }

    @Test
    void result_failure_should_carry_reason_and_no_values() {
        CollectResult result = CollectResult.failure("连接超时");

        assertThat(result.connected()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("连接超时");
        assertThat(result.values()).isEmpty();
    }

    @Test
    void failed_point_should_never_have_blank_reason() {
        // 空原因的失败日志在排障时等于没有日志：无法判断是超时还是地址错配
        PointValue value = PointValue.failed(1L, 100L, "temp", null);
        assertThat(value.errorMessage()).isEqualTo("未知采集失败");

        assertThat(PointValue.failed(1L, 100L, "temp", "  ").errorMessage())
                .isEqualTo("未知采集失败");
    }

    @Test
    void ok_point_should_carry_both_raw_and_engineered_value() {
        // 排查「平台显示 3.6 但仪表盘是 0」时必须能同时看到原始值与工程值，
        // 否则无法判断是点表换算配错还是设备本身异常
        PointValue value = PointValue.ok(1L, 100L, "temp", "36", "3.6");

        assertThat(value.success()).isTrue();
        assertThat(value.rawValue()).isEqualTo("36");
        assertThat(value.value()).isEqualTo("3.6");
        assertThat(value.errorMessage()).isNull();
    }

    @Test
    void collect_result_should_tolerate_null_values_list() {
        assertThat(new CollectResult(null, true, null).values()).isEmpty();
    }

    private static DevicePointTable point(Long id) {
        DevicePointTable p = new DevicePointTable();
        p.setId(id);
        p.setDeviceId(100L);
        p.setPointKey("p" + id);
        return p;
    }
}
