package com.mqtt.cloud.common.util;

/**
 * 公开列表接口的分页归一化。
 * <p>
 * 控制器 / DTO 的默认值不能替代服务层校验：显式传入 0、负数或超大 pageSize 时，
 * 仍必须在进入 SQL 前收敛，否则会把客户端参数直接变成数据库压力。
 */
public final class PageSupport {

    public static final int MAX_PAGE_SIZE = 100;

    private PageSupport() {
    }

    public static int page(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public static int size(Integer pageSize, int defaultSize) {
        if (pageSize == null || pageSize < 1) {
            return defaultSize;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
