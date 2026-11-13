package com.xinyu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatMessageRequest {

    @NotBlank(message = "消息不能为空")
    @Size(max = 1000, message = "消息太长啦，最多 1000 个字")
    private String content;
}
