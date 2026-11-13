package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("treehole_post")
public class TreeholePost {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String content;
    private String images;
    private Integer likeCount;
    private Integer commentCount;
    private Integer status;
    private Integer isAnonymous;
    private LocalDateTime createdAt;
}
