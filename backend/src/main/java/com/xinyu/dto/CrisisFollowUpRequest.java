package com.xinyu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 危机预警回访：处理完之后记录一次「再联系」的结果
 */
@Data
public class CrisisFollowUpRequest {

    @NotBlank(message = "请填写回访情况")
    @Size(max = 200, message = "回访情况最多 200 字")
    private String note;
}
