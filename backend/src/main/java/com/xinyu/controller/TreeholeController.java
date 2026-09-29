// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.controller;

import com.xinyu.common.PageResult;
import com.xinyu.common.Result;
import com.xinyu.dto.CommentRequest;
import com.xinyu.dto.CommentVO;
import com.xinyu.dto.TreeholePostRequest;
import com.xinyu.dto.TreeholePostVO;
import com.xinyu.security.SecurityUtil;
import com.xinyu.service.TreeholeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 匿名树洞接口
 */
@RestController
@RequestMapping("/api/treehole")
@RequiredArgsConstructor
public class TreeholeController {

    private final TreeholeService treeholeService;

    @GetMapping("/posts")
    public Result<PageResult<TreeholePostVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(treeholeService.list(SecurityUtil.getCurrentUserId(), page, size));
    }

    @PostMapping("/posts")
    public Result<TreeholePostVO> create(@Valid @RequestBody TreeholePostRequest req) {
        return Result.ok(treeholeService.create(SecurityUtil.getCurrentUserId(), req));
    }

    @GetMapping("/posts/{id}")
    public Result<TreeholePostVO> detail(@PathVariable Long id) {
        return Result.ok(treeholeService.detail(SecurityUtil.getCurrentUserId(), id));
    }

    @PostMapping("/posts/{id}/like")
    public Result<Map<String, Object>> like(@PathVariable Long id) {
        return Result.ok(treeholeService.like(SecurityUtil.getCurrentUserId(), id));
    }

    @DeleteMapping("/posts/{id}/like")
    public Result<Map<String, Object>> unlike(@PathVariable Long id) {
        return Result.ok(treeholeService.unlike(SecurityUtil.getCurrentUserId(), id));
    }

    @GetMapping("/posts/{id}/comments")
    public Result<PageResult<CommentVO>> comments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(treeholeService.comments(id, page, size));
    }

    @PostMapping("/posts/{id}/comments")
    public Result<CommentVO> addComment(@PathVariable Long id, @Valid @RequestBody CommentRequest req) {
        return Result.ok(treeholeService.addComment(SecurityUtil.getCurrentUserId(), id, req));
    }
}
