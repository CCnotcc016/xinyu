package com.xinyu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "请输入原密码")
    private String oldPassword;

    @NotBlank(message = "请输入新密码")
    @Size(min = 8, max = 32, message = "新密码长度需为 8-32 位")
    @Pattern(regexp = "^\\S+$", message = "新密码不能包含空格")
    private String newPassword;
}
