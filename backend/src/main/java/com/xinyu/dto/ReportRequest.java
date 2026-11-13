package com.xinyu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReportRequest {

    /** POST / COMMENT / USER */
    @NotBlank(message = "举报对象类型不能为空")
    private String targetType;

    @NotNull(message = "举报对象 ID 不能为空")
    private Long targetId;

    @Size(max = 500, message = "举报理由过长")
    private String reason;
}
