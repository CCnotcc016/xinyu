package com.xinyu.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 定位 / 天气授权开关。同意和撤回都走这里。
 */
@Data
public class LocationConsentRequest {

    @NotNull(message = "缺少授权状态")
    private Boolean consent;
}
