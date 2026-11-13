package com.xinyu.controller;

import com.xinyu.common.PageResult;
import com.xinyu.common.Result;
import com.xinyu.dto.CalendarVO;
import com.xinyu.dto.DiaryRequest;
import com.xinyu.dto.DiaryVO;
import com.xinyu.dto.DistributionVO;
import com.xinyu.dto.EmotionInsightVO;
import com.xinyu.dto.RecapVO;
import com.xinyu.dto.TrendPoint;
import com.xinyu.dto.WeeklyReportVO;
import com.xinyu.security.SecurityUtil;
import com.xinyu.service.DiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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

import java.time.LocalDate;
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
    @PreAuthorize("!hasRole('STAFF')")
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
    public Result<List<TrendPoint>> trend(
            @RequestParam(defaultValue = "week") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return Result.ok(diaryService.trend(SecurityUtil.getCurrentUserId(), range, end));
    }

    /** 情绪洞察：天气 / 场景 / 标签 / 星期与分数的关系 */
    @GetMapping("/stats/insights")
    public Result<EmotionInsightVO> insights() {
        return Result.ok(diaryService.insights(SecurityUtil.getCurrentUserId()));
    }

    @GetMapping("/stats/distribution")
    public Result<DistributionVO> distribution() {
        return Result.ok(diaryService.distribution(SecurityUtil.getCurrentUserId()));
    }

    /** 情绪日历：默认近 13 周（够铺满一屏热力图） */
    @GetMapping("/stats/calendar")
    public Result<CalendarVO> calendar(@RequestParam(defaultValue = "91") int days) {
        return Result.ok(diaryService.calendar(SecurityUtil.getCurrentUserId(), days));
    }

    /** 月度 / 年度回顾：period=month|year，anchor 传该期间的任意一天（缺省今天） */
    @GetMapping("/stats/recap")
    public Result<RecapVO> recap(
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchor) {
        return Result.ok(diaryService.recap(SecurityUtil.getCurrentUserId(), period, anchor));
    }

    /** AI 情绪周报：weekStart 传任意一天即可，服务端会归一到那一周的周一；refresh=true 强制重算 */
    @GetMapping("/stats/weekly-report")
    public Result<WeeklyReportVO> weeklyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return Result.ok(diaryService.weeklyReport(SecurityUtil.getCurrentUserId(), weekStart, refresh));
    }

    @GetMapping("/{id}")
    public Result<DiaryVO> get(@PathVariable Long id) {
        return Result.ok(diaryService.get(SecurityUtil.getCurrentUserId(), id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("!hasRole('STAFF')")
    public Result<DiaryVO> update(@PathVariable Long id, @Valid @RequestBody DiaryRequest req) {
        return Result.ok(diaryService.update(SecurityUtil.getCurrentUserId(), id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("!hasRole('STAFF')")
    public Result<Void> delete(@PathVariable Long id) {
        diaryService.delete(SecurityUtil.getCurrentUserId(), id);
        return Result.ok();
    }

    /** 回收站：7 天内删除的日记，可以恢复或彻底删除 */
    @GetMapping("/trash")
    public Result<List<DiaryVO>> trash() {
        return Result.ok(diaryService.trash(SecurityUtil.getCurrentUserId()));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("!hasRole('STAFF')")
    public Result<DiaryVO> restore(@PathVariable Long id) {
        return Result.ok(diaryService.restore(SecurityUtil.getCurrentUserId(), id));
    }

    @DeleteMapping("/trash/{id}")
    @PreAuthorize("!hasRole('STAFF')")
    public Result<Void> purge(@PathVariable Long id) {
        diaryService.purge(SecurityUtil.getCurrentUserId(), id);
        return Result.ok();
    }

    @GetMapping(value = "/{id}/ai-reply/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aiReplyStream(@PathVariable Long id) {
        return diaryService.aiReplyStream(SecurityUtil.getCurrentUserId(), id);
    }
}
