package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.SceneVersion;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 场景版本快照 Mapper（T-51）。
 */
@Mapper
public interface SceneVersionMapper extends BaseMapper<SceneVersion> {

    /** 某场景的全部版本，按版本号倒序（最新在前）。 */
    @Select("SELECT * FROM scene_version WHERE scene_id = #{sceneId} ORDER BY version_no DESC")
    List<SceneVersion> selectBySceneId(@Param("sceneId") Long sceneId);

    /** 当前最大版本号；无版本返回 0。 */
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM scene_version WHERE scene_id = #{sceneId}")
    int selectMaxVersionNo(@Param("sceneId") Long sceneId);

    /** 保留上限之外的旧版本清理。 */
    @Delete("DELETE FROM scene_version WHERE scene_id = #{sceneId} AND version_no <= #{maxVersionNo}")
    int deleteOlderThan(@Param("sceneId") Long sceneId, @Param("maxVersionNo") int maxVersionNo);

    /** 场景删除时清理（场景为逻辑删除，外键级联不会触发）。 */
    @Delete("DELETE FROM scene_version WHERE scene_id = #{sceneId}")
    int deleteBySceneId(@Param("sceneId") Long sceneId);
}
