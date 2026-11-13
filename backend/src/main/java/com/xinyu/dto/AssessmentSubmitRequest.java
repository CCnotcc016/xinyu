package com.xinyu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AssessmentSubmitRequest {

    @NotBlank(message = "请选择量表")
    private String scaleKey;

    /** 逐题得分，顺序必须和题目顺序一致 */
    @NotEmpty(message = "请完成全部题目后再提交")
    private List<Integer> answers;
}
