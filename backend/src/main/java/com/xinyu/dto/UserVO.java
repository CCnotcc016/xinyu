// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.dto;

import com.xinyu.entity.User;
import lombok.Data;

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
