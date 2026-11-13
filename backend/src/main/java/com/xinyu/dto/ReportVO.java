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
    /** 处理说明，未处理时为 null */
    private String remark;
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
        vo.remark = report.getRemark();
        vo.createdAt = report.getCreatedAt();
        return vo;
    }
}
