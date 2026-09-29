// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.service;

import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.dto.ReportRequest;
import com.xinyu.entity.Report;
import com.xinyu.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 举报服务
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final List<String> TARGET_TYPES = List.of("POST", "COMMENT", "USER");

    private final ReportMapper reportMapper;

    public void create(Long reporterId, ReportRequest req) {
        String type = req.getTargetType() == null ? "" : req.getTargetType().toUpperCase();
        if (!TARGET_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        Report report = new Report();
        report.setTargetType(type);
        report.setTargetId(req.getTargetId());
        report.setReporterId(reporterId);
        report.setReason(req.getReason());
        report.setStatus("PENDING");
        reportMapper.insert(report);
    }
}
