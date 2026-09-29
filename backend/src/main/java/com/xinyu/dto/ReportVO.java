// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.dto;

import com.xinyu.entity.Report;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报展示对象（含举报人昵称与目标内容预览）
 */
@Data
public class ReportVO {

    private Long id;
    private String targetType;
    private Long targetId;
    private String reason;
    private String status;
    private Long reporterId;
    private String reporterName;
    private String targetContent;
    private LocalDateTime createdAt;

    public static ReportVO from(Report report, String reporterName, String targetContent) {
        ReportVO vo = new ReportVO();
        vo.id = report.getId();
        vo.targetType = report.getTargetType();
        vo.targetId = report.getTargetId();
        vo.reason = report.getReason();
        vo.status = report.getStatus();
        vo.reporterId = report.getReporterId();
        vo.reporterName = reporterName;
        vo.targetContent = targetContent;
        vo.createdAt = report.getCreatedAt();
        return vo;
    }
}
