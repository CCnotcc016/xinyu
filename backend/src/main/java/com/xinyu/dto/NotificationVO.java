package com.xinyu.dto;

import com.xinyu.entity.Notification;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationVO {

    private Long id;
    private String type;
    private String title;
    private String content;
    private String targetType;
    private Long targetId;
    private Boolean read;
    private LocalDateTime createdAt;

    public static NotificationVO from(Notification n) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setType(n.getType());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setTargetType(n.getTargetType());
        vo.setTargetId(n.getTargetId());
        vo.setRead(n.getIsRead() != null && n.getIsRead() == 1);
        vo.setCreatedAt(n.getCreatedAt());
        return vo;
    }
}
