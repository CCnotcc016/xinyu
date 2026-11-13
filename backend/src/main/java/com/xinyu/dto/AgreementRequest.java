package com.xinyu.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 确认同意现行协议。老账号登录后由「政策更新」弹窗调用。
 */
@Data
public class AgreementRequest {

    /** 前端所同意的那一版协议版本号；与服务端现行版本不一致会被拒绝，避免拿着旧文本点同意 */
    @NotBlank(message = "缺少协议版本号")
    private String version;
}
