package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mqtt.cloud.dto.request.ChangePasswordDTO;
import com.mqtt.cloud.dto.request.RegisterDTO;
import com.mqtt.cloud.dto.response.LoginResponseDTO;
import com.mqtt.cloud.dto.response.UserResponseDTO;
import com.mqtt.cloud.entity.User;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     */
    UserResponseDTO register(RegisterDTO dto);

    /**
     * 用户登录，返回访问令牌与用户信息
     */
    LoginResponseDTO login(String username, String rawPassword);

    /**
     * 获取当前登录用户信息
     */
    UserResponseDTO getCurrentUser(Long userId);

    /**
     * 用户登出，将 Token 加入黑名单
     */
    void logout(String token);

    /**
     * 修改密码
     */
    void changePassword(Long userId, ChangePasswordDTO dto);
}