package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.SceneStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 场景步骤 Mapper（T-23 设计文档 §6.2）。
 * <p>
 * 步骤**无逻辑删除列**：场景更新采用「整体替换步骤」（{@link #deleteBySceneId} 后重新插入），
 * 因此本表不参与全局逻辑删除过滤。
 */
@Mapper
public interface SceneStepMapper extends BaseMapper<SceneStep> {

    /** 某场景的全部步骤，按 {@code seq} 升序。 */
    List<SceneStep> selectBySceneId(@Param("sceneId") Long sceneId);

    /** 删除某场景的全部步骤（整体替换用，物理删除）。 */
    int deleteBySceneId(@Param("sceneId") Long sceneId);
}