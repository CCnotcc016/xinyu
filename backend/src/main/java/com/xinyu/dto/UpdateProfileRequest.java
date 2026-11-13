package com.xinyu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人资料
 */
@Data
public class UpdateProfileRequest {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 20, message = "昵称长度不能超过 20 位")
    private String nickname;
}
