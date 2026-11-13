package com.xinyu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员处理危机预警
 */
@Data
public class CrisisAlertHandleRequest {

    @NotBlank(message = "处理结果不能为空")
    private String status;

    @Size(max = 200, message = "备注最多 200 字")
    private String remark;
}
