package com.mqtt.cloud.collect.modbus;

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.mqtt.cloud.config.ModbusCollectProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Modbus 主站连接注册与生命周期（T-31 P4）。
 * <p>
 * 按 T-31 约束<b>不做常驻连接池</b>：每轮采集建立连接、读完立即关闭，
 * 避免应用启动时被现场网络抖动拖住 readiness。这里只做两件事：
 * <ul>
 *   <li>用信号量限制并发连接数，防止一次巡检把现场设备 / NAT 会话打满；</li>
 *   <li>统一在 {@link #close(ModbusTCPMaster)} 里断开并释放许可，采集器只需 finally 调用一次。</li>
 * </ul>
 */
@Slf4j
@Component
public class ModbusMasterRegistry {

    private final Semaphore permits;
    private final Set<ModbusTCPMaster> active = ConcurrentHashMap.newKeySet();

    public ModbusMasterRegistry(ModbusCollectProperties properties) {
        this.permits = new Semaphore(Math.max(1, properties.getMaxConnections()), true);
    }

    /**
     * 建立连接。失败时归还许可并抛出，由采集器转成「全部点位 FAILED」。
     */
    public ModbusTCPMaster open(ModbusConnectionConfig config) throws Exception {
        int timeoutMs = (int) Math.max(1, config.connectTimeout().toMillis());
        if (!permits.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new IOException("Modbus 并发连接数已达上限（" + timeoutMs + "ms 内未取得许可）");
        }
        ModbusTCPMaster master = null;
        try {
            master = new ModbusTCPMaster(config.host(), config.port(), timeoutMs, false);
            master.connect();
            master.setTimeout((int) Math.max(1, config.readTimeout().toMillis()));
            master.setRetries(config.retry());
            active.add(master);
            return master;
        } catch (Exception e) {
            if (master != null) {
                disconnectQuietly(master);
            }
            permits.release();
            throw e;
        }
    }

    /** 断开连接并归还许可；重复调用幂等。 */
    public void close(ModbusTCPMaster master) {
        if (master == null) {
            return;
        }
        boolean tracked = active.remove(master);
        disconnectQuietly(master);
        if (tracked) {
            permits.release();
        }
    }

    /** 当前打开连接数，供指标 / 单测使用。 */
    public int activeCount() {
        return active.size();
    }

    private void disconnectQuietly(ModbusTCPMaster master) {
        try {
            master.disconnect();
        } catch (Exception e) {
            log.debug("Modbus 断开连接失败（忽略）: {}", e.getMessage());
        }
    }
}
