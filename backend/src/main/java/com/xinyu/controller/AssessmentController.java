package com.xinyu.controller;

import com.xinyu.common.Result;
import com.xinyu.dto.AssessmentSubmitRequest;
import com.xinyu.dto.AssessmentVO;
import com.xinyu.dto.ScaleVO;
import com.xinyu.security.SecurityUtil;
import com.xinyu.service.AssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 心理自评量表接口。
 *
 * <p>所有结果都只是筛查参考，不是诊断；返回体里始终带 disclaimer 字段，前端原样展示。</p>
 */
@RestController
@RequestMapping("/api/assessments")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    /** 量表列表 + 题目定义 */
    @GetMapping("/scales")
    public Result<List<ScaleVO>> scales() {
        return Result.ok(assessmentService.scales());
    }

    @PostMapping
    @PreAuthorize("!hasRole('STAFF')")
    public Result<AssessmentVO> submit(@Valid @RequestBody AssessmentSubmitRequest req) {
        return Result.ok(assessmentService.submit(SecurityUtil.getCurrentUserId(), req));
    }

    @GetMapping
    public Result<List<AssessmentVO>> history(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(assessmentService.history(SecurityUtil.getCurrentUserId(), limit));
    }

    @GetMapping("/{id}")
    public Result<AssessmentVO> detail(@PathVariable Long id) {
        return Result.ok(assessmentService.detail(SecurityUtil.getCurrentUserId(), id));
    }

    /** 重新生成 AI 解读（模型当时不可用时可重试） */
    @PostMapping("/{id}/interpretation")
    public Result<AssessmentVO> interpret(@PathVariable Long id) {
        return Result.ok(assessmentService.interpret(SecurityUtil.getCurrentUserId(), id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        assessmentService.delete(SecurityUtil.getCurrentUserId(), id);
        return Result.ok(null);
    }
}
