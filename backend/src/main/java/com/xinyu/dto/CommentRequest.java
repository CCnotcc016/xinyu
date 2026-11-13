package com.xinyu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentRequest {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论内容过长")
    private String content;

    /** 回复某条评论时带上它的 id；直接评论帖子则为空 */
    private Long parentId;
}
