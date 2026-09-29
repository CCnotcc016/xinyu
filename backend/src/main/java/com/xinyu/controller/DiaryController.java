// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.controller;

import com.xinyu.common.PageResult;
import com.xinyu.common.Result;
import com.xinyu.dto.DiaryRequest;
import com.xinyu.dto.DiaryVO;
import com.xinyu.dto.DistributionVO;
import com.xinyu.dto.TrendPoint;
import com.xinyu.security.SecurityUtil;
import com.xinyu.service.DiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日记接口
 */
@RestController
@RequestMapping("/api/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @PostMapping
    public Result<DiaryVO> create(@Valid @RequestBody DiaryRequest req) {
        return Result.ok(diaryService.create(SecurityUtil.getCurrentUserId(), req));
    }

    @GetMapping
    public Result<PageResult<DiaryVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return Result.ok(diaryService.page(SecurityUtil.getCurrentUserId(), page, size, start, end));
    }

    @GetMapping("/stats/trend")
    public Result<List<TrendPoint>> trend(@RequestParam(defaultValue = "week") String range) {
        return Result.ok(diaryService.trend(SecurityUtil.getCurrentUserId(), range));
    }

    @GetMapping("/stats/distribution")
    public Result<DistributionVO> distribution() {
        return Result.ok(diaryService.distribution(SecurityUtil.getCurrentUserId()));
    }

    @GetMapping("/{id}")
    public Result<DiaryVO> get(@PathVariable Long id) {
        return Result.ok(diaryService.get(SecurityUtil.getCurrentUserId(), id));
    }

    @PutMapping("/{id}")
    public Result<DiaryVO> update(@PathVariable Long id, @Valid @RequestBody DiaryRequest req) {
        return Result.ok(diaryService.update(SecurityUtil.getCurrentUserId(), id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        diaryService.delete(SecurityUtil.getCurrentUserId(), id);
        return Result.ok();
    }

    @GetMapping(value = "/{id}/ai-reply/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aiReplyStream(@PathVariable Long id) {
        return diaryService.aiReplyStream(SecurityUtil.getCurrentUserId(), id);
    }
}
