// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员视角的危机预警。
 *
 * <p>这里会带上发帖人注册时填写的手机号 <b>明文</b>（普通接口一律打码）——
 * 这是刻意的例外：管理员要能在紧急情况下联系到人。所以这个接口
 * 只开放给 ROLE_ADMIN，并且后台页面明确标注「仅用于危机干预」。</p>
 */
@Data
public class CrisisAlertVO {

    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    /** 注册手机号（明文，仅管理员可见）；未绑定则为 null */
    private String phone;
    private String targetType;
    private Long targetId;
    private String keyword;
    private String content;
    private String status;
    private String remark;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
}
