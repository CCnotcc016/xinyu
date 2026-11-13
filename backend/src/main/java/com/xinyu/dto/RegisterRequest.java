package com.xinyu.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度需在 3-20 位之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名仅支持字母、数字、下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度需在 8-32 位之间")
    private String password;

    @Size(max = 20, message = "昵称长度不能超过 20 位")
    private String nickname;

    /** 注册必须填手机号：既是账号找回凭据，也是危机干预时的联系方式 */
    @NotBlank(message = "请填写手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "请填写短信验证码")
    @Pattern(regexp = "^\\d{6}$", message = "短信验证码为 6 位数字")
    private String smsCode;

    /**
     * 是否已勾选同意《用户协议》《隐私政策》《免责声明》。
     *
     * <p>用包装类型而不是 boolean：字段缺失时 Jackson 会留 null，@AssertTrue 对 null 是放行的，
     * 所以必须再叠一个 @NotNull，否则伪造一个不带该字段的请求就能跳过同意。</p>
     */
    @NotNull(message = "请先阅读并同意《用户协议》与《隐私政策》")
    @AssertTrue(message = "请先阅读并同意《用户协议》与《隐私政策》")
    private Boolean agreementAccepted;
}
