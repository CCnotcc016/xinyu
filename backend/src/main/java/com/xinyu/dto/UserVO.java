package com.xinyu.dto;

import com.xinyu.common.Legal;
import com.xinyu.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 脱敏后的用户信息
 *
 * <p>手机号属于敏感信息，对外一律只给掩码（138****8000），
 * 完整号码只有管理员在「求助」页做危机干预时才看得到。</p>
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    /** 掩码手机号，未绑定时为 null */
    private String phone;
    /** 是否已绑定手机号，前端据此决定要不要弹绑定提示 */
    private Boolean phoneBound;
    private String role;
    private Integer status;
    /** 已同意的协议版本，从未同意过时为 null */
    private String agreementVersion;
    /** 同意时间，作为「用户确实同意过」的凭证 */
    private LocalDateTime agreementAcceptedAt;
    /** 现行协议版本是否已确认；false 时前端会强制弹出「政策更新」 */
    private Boolean agreementAccepted;
    /** 是否授权用定位换天气，设置页的开关就绑这个 */
    private Boolean locationConsent;
    /** 登录锁定到期时间，未锁定为 null；后台据此显示「已锁定」与解锁按钮 */
    private LocalDateTime lockedUntil;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.id = user.getId();
        vo.username = user.getUsername();
        vo.nickname = user.getNickname();
        vo.avatar = user.getAvatar();
        vo.phone = maskPhone(user.getPhone());
        vo.phoneBound = user.getPhone() != null && !user.getPhone().isBlank();
        vo.role = user.getRole();
        vo.status = user.getStatus();
        vo.agreementVersion = user.getAgreementVersion();
        vo.agreementAcceptedAt = user.getAgreementAcceptedAt();
        // 比对的是「现版本」，所以每次改法律文本都会自动让所有老用户重新确认一次
        vo.agreementAccepted = Legal.CURRENT_VERSION.equals(user.getAgreementVersion());
        vo.locationConsent = user.getLocationConsent() != null && user.getLocationConsent() == 1;
        vo.lockedUntil = user.getLockedUntil();
        return vo;
    }

    /** 138****8000；长度不足时退化为全星号，避免越界 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        if (phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
