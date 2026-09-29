package com.mqtt.cloud.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 时间字段自动填充。
 * <p>
 * 实体上声明了 {@code fill = FieldFill.INSERT / INSERT_UPDATE} 的字段不会生成 {@code <if>} 判空，
 * 缺少本处理器时这些列会被显式写入 NULL（createdAt / updatedAt / receivedAt），时间字段在库里恒为空。
 */
@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        setIfNull(metaObject, "createdAt", now);
        setIfNull(metaObject, "updatedAt", now);
        setIfNull(metaObject, "receivedAt", now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 更新时无条件刷新：实体通常由 getById 载入，updatedAt 是库中的旧值，仅在为空时填充等于没填
        setAlways(metaObject, "updatedAt", LocalDateTime.now());
    }

    private void setIfNull(MetaObject metaObject, String field, Object value) {
        if (!writable(metaObject, field)) {
            return;
        }
        if (metaObject.getValue(field) == null) {
            metaObject.setValue(field, value);
        }
    }

    private void setAlways(MetaObject metaObject, String field, Object value) {
        if (writable(metaObject, field)) {
            metaObject.setValue(field, value);
        }
    }

    private boolean writable(MetaObject metaObject, String field) {
        return metaObject != null
                && metaObject.getOriginalObject() != null
                && metaObject.hasSetter(field);
    }
}