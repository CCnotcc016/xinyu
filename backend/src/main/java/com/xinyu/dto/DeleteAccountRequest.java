package com.xinyu.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 注销账号：需要输入登录密码二次确认
 */
@Data
public class DeleteAccountRequest {

    @NotBlank(message = "请输入登录密码")
    private String password;
}
