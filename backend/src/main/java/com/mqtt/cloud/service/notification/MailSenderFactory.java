package com.mqtt.cloud.service.notification;

import com.mqtt.cloud.config.NotificationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Properties;

/**
 * 邮件发送器工厂（T-27 设计文档 §4.3 / §12）。
 * <p>
 * 渠道行上的 {@code config} 优先于全局 {@code spring.mail.*}：行内给出 {@code host} 时按其
 * 构建一次性 {@link JavaMailSenderImpl}（含连接 / 读 / 写超时，取自 {@code delivery.timeout-ms}）；
 * 未给出时回退到 Spring Boot 自动装配的全局发送器。
 * <p>
 * 拆成独立组件而非写在 {@code EmailChannel} 内，是为了让渠道的投递逻辑可在单测中不触碰真实 SMTP。
 */
@Component
public class MailSenderFactory {

    private final ObjectProvider<JavaMailSender> globalProvider;
    private final NotificationProperties properties;

    public MailSenderFactory(ObjectProvider<JavaMailSender> globalProvider, NotificationProperties properties) {
        this.globalProvider = globalProvider;
        this.properties = properties;
    }

    /**
     * 按渠道行配置构建发送器。
     *
     * @throws IllegalStateException 行内无 {@code host} 且全局也未配置 {@code spring.mail.host}
     */
    public JavaMailSender create(Map<String, Object> config) {
        String host = text(config.get("host"));
        if (host == null || host.isBlank()) {
            JavaMailSender global = globalProvider.getIfAvailable();
            if (global == null) {
                throw new IllegalStateException("未配置邮件服务器：渠道缺少 host 且未设置 spring.mail.host");
            }
            return global;
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port(config.get("port")));
        String username = text(config.get("username"));
        if (username != null && !username.isBlank()) {
            sender.setUsername(username);
        }
        String password = text(config.get("password"));
        if (password != null && !password.isBlank()) {
            sender.setPassword(password);
        }

        Properties mailProperties = sender.getJavaMailProperties();
        if (bool(config.get("ssl"))) {
            mailProperties.put("mail.smtp.ssl.enable", "true");
        }
        int timeout = properties.getDelivery().getTimeoutMs();
        mailProperties.put("mail.smtp.connectiontimeout", String.valueOf(timeout));
        mailProperties.put("mail.smtp.timeout", String.valueOf(timeout));
        mailProperties.put("mail.smtp.writetimeout", String.valueOf(timeout));
        return sender;
    }

    private int port(Object value) {
        if (value == null) {
            return 25;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return 25;
        }
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && Boolean.parseBoolean(value.toString().trim());
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }
}