package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report")
public class Report {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String targetType;
    private Long targetId;
    private Long reporterId;
    private String reason;
    private String status;
    /** 处理说明：管理员处理时填写的备注，举报人在「我的举报」里能看到 */
    private String remark;
    private LocalDateTime createdAt;
}
