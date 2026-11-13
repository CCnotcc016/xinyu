package com.xinyu.controller;

import com.xinyu.common.PageResult;
import com.xinyu.common.Result;
import com.xinyu.dto.ReportVO;
import com.xinyu.dto.ReportRequest;
import com.xinyu.security.SecurityUtil;
import com.xinyu.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 举报接口
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** 我发起的举报与处理结果（举报回执） */
    @GetMapping("/mine")
    @PreAuthorize("!hasRole('STAFF')")
    public Result<PageResult<ReportVO>> mine(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(reportService.mine(SecurityUtil.getCurrentUserId(), page, size));
    }

    @PostMapping
    @PreAuthorize("!hasRole('STAFF')")
    public Result<Void> create(@Valid @RequestBody ReportRequest req) {
        reportService.create(SecurityUtil.getCurrentUserId(), req);
        return Result.ok();
    }
}
