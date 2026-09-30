package com.mqtt.cloud.common.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 把副作用推迟到事务提交后执行。
 * <p>
 * 用于「调用外部接口」这类不可回滚、且耗时不可控的动作：若直接写在 {@code @Transactional}
 * 方法里，网络 I/O 期间会一直占用数据库连接（EMQX 不可达时最坏可达连接超时 + 读超时）。
 * 提交后执行可让业务事务尽快释放连接，同时保证只在状态真正落库后才产生外部副作用。
 * <p>
 * 无事务时立即执行，便于单元测试与未开启事务的调用路径。
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}