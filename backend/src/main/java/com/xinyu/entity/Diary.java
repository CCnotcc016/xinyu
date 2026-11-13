package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("diary")
public class Diary {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String content;
    private String voiceUrl;
    private Integer emotionScore;
    private String emotionLabel;
    private String weather;
    private String scene;
    private String privacy;
    private String aiReply;
    private String aiStatus;
    // 离线补传的幂等键：App 先本地保存，联网后带着同一串 client_uid 重传，
    // 服务端按 (user_id, client_uid) 判重，超时重试不会写成两条
    private String clientUid;
    private LocalDateTime createdAt;
    // 不参与 UPDATE：MyBatis-Plus 若把实体里读出来的旧值写回，会覆盖掉
    // 数据库的 ON UPDATE CURRENT_TIMESTAMP，导致 updated_at 永远不变
    @TableField(value = "updated_at", updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
    // 逻辑删除：删除只是把 deleted_at 写成当前时间，7 天内可以在「回收站」里恢复。
    // 有了这个注解，MyBatis-Plus 会在所有自动生成的查询里补上 deleted_at IS NULL，
    // 统计 / 趋势 / 列表都不用挨个改。
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}
