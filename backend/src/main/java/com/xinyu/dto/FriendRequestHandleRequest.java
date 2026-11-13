package com.xinyu.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FriendRequestHandleRequest {

    /** ACCEPT 同意 / REJECT 拒绝 */
    @NotBlank(message = "请指定处理方式")
    private String action;
}
