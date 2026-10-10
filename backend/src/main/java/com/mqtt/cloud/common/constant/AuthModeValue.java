package com.mqtt.cloud.common.constant;

/**
 * 产品认证方式取值，与 product.auth_mode 字段一致（T-25）。
 * <p>
 * 三种方式在同一回调内分派：{@link #SECRET} 走一机一密口令校验，
 * {@link #X509} 走 mTLS 证书主体归属校验，{@link #JWT} 走设备令牌验签。
 * 取值集合集中在此处维护，校验、分派与前端选项都以它为准，
 * 避免同一组字符串散落在注解、比较与配置中。
 */
public final class AuthModeValue {

    public static final String SECRET = "SECRET";
    public static final String X509 = "X509";
    public static final String JWT = "JWT";

    private AuthModeValue() {
    }
}
