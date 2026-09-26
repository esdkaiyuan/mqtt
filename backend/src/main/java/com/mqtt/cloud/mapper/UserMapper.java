package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 根据用户名查找用户（包含已删除的） */
    User findByUsername(@Param("username") String username);

    /** 查询所有活跃用户 */
    List<User> findActiveUsers();

    /** 根据角色查询用户 */
    List<User> findByRole(@Param("role") String role);

    /** 更新最后登录时间 */
    int updateLastLogin(@Param("id") Long id, @Param("lastLogin") java.time.LocalDateTime lastLogin);
}
