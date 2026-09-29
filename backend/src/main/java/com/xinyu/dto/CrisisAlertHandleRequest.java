// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
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
