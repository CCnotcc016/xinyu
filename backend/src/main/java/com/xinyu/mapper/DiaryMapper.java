package com.xinyu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyu.entity.Diary;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DiaryMapper extends BaseMapper<Diary> {

    /**
     * 回收站列表。逻辑删除后 MyBatis-Plus 会自动过滤掉这些行，
     * 所以这里用自定义 SQL 把 deleted_at 不为空的记录捞出来。
     */
    @Select("SELECT * FROM diary WHERE user_id = #{userId} AND deleted_at IS NOT NULL "
            + "ORDER BY deleted_at DESC")
    List<Diary> selectTrash(@Param("userId") Long userId);

    @Select("SELECT * FROM diary WHERE id = #{id} AND deleted_at IS NOT NULL")
    Diary selectTrashed(@Param("id") Long id);

    /** 恢复：MP 的 update 会自动补 deleted_at IS NULL，只能走自定义 SQL */
    @Update("UPDATE diary SET deleted_at = NULL, updated_at = NOW() WHERE id = #{id}")
    int restoreById(@Param("id") Long id);

    /** 永久删除 (回收站的「永久删除」与过期自动清理) */
    @Delete("DELETE FROM diary WHERE id = #{id} AND deleted_at IS NOT NULL")
    int purgeById(@Param("id") Long id);

    /** 超过保留期的回收站记录 ID */
    @Select("SELECT id FROM diary WHERE user_id = #{userId} AND deleted_at IS NOT NULL "
            + "AND deleted_at < #{before}")
    List<Long> selectExpiredIds(@Param("userId") Long userId, @Param("before") LocalDateTime before);

    /**
     * 离线补传的幂等查询：按 (user_id, client_uid) 找已存在的那条。
     * 走自定义 SQL 是为了不被逻辑删除过滤掉 —— 万一用户删了之后客户端才重传，
     * 也要认出来「这篇早就存过了」，而不是让唯一索引报一次冲突。
     */
    @Select("SELECT * FROM diary WHERE user_id = #{userId} AND client_uid = #{clientUid} LIMIT 1")
    Diary selectByClientUid(@Param("userId") Long userId, @Param("clientUid") String clientUid);
}
