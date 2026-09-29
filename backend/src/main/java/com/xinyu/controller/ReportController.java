// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.controller;

import com.xinyu.common.Result;
import com.xinyu.dto.ReportRequest;
import com.xinyu.security.SecurityUtil;
import com.xinyu.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 举报接口
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public Result<Void> create(@Valid @RequestBody ReportRequest req) {
        reportService.create(SecurityUtil.getCurrentUserId(), req);
        return Result.ok();
    }
}
