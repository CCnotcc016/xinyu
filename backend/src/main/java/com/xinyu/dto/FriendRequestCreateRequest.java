package com.xinyu.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FriendRequestCreateRequest {

    @NotNull(message = "请选择要添加的用户")
    private Long toUserId;

    @Size(max = 100, message = "附言最多 100 个字")
    private String message;
}
