package com.xinyu.controller;

import com.xinyu.common.PageResult;
import com.xinyu.common.Result;
import com.xinyu.dto.CommentRequest;
import com.xinyu.dto.CommentVO;
import com.xinyu.dto.TreeholePostRequest;
import com.xinyu.dto.TreeholePostVO;
import com.xinyu.security.SecurityUtil;
import com.xinyu.service.ImageUploadService;
import com.xinyu.service.TreeholeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 匿名树洞接口
 */
@RestController
@RequestMapping("/api/treehole")
@RequiredArgsConstructor
public class TreeholeController {

    private final TreeholeService treeholeService;
    private final ImageUploadService imageUploadService;

    @GetMapping("/posts")
    public Result<PageResult<TreeholePostVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(treeholeService.list(SecurityUtil.getCurrentUserId(), page, size));
    }

    @PostMapping("/posts")
    @PreAuthorize("!hasRole('STAFF')")
    public Result<TreeholePostVO> create(@Valid @RequestBody TreeholePostRequest req) {
        return Result.ok(treeholeService.create(SecurityUtil.getCurrentUserId(), req));
    }

    /** 发帖前先把图传上来，返回的 url 直接放进 TreeholePostRequest.images */
    @PostMapping("/images")
    @PreAuthorize("!hasRole('STAFF')")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.ok(Map.of("url",
                imageUploadService.uploadPostImage(SecurityUtil.getCurrentUserId(), file)));
    }

    @GetMapping("/posts/{id}")
    public Result<TreeholePostVO> detail(@PathVariable Long id) {
        return Result.ok(treeholeService.detail(SecurityUtil.getCurrentUserId(), id));
    }

    @PostMapping("/posts/{id}/like")
    @PreAuthorize("!hasRole('STAFF')")
    public Result<Map<String, Object>> like(@PathVariable Long id) {
        return Result.ok(treeholeService.like(SecurityUtil.getCurrentUserId(), id));
    }

    @DeleteMapping("/posts/{id}/like")
    @PreAuthorize("!hasRole('STAFF')")
    public Result<Map<String, Object>> unlike(@PathVariable Long id) {
        return Result.ok(treeholeService.unlike(SecurityUtil.getCurrentUserId(), id));
    }

    @GetMapping("/posts/{id}/comments")
    public Result<PageResult<CommentVO>> comments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(treeholeService.comments(SecurityUtil.getCurrentUserId(), id, page, size));
    }

    @PostMapping("/posts/{id}/comments")
    @PreAuthorize("!hasRole('STAFF')")
    public Result<CommentVO> addComment(@PathVariable Long id, @Valid @RequestBody CommentRequest req) {
        return Result.ok(treeholeService.addComment(SecurityUtil.getCurrentUserId(), id, req));
    }

    /** 贴主置顶/取消置顶评论区的一条留言（可以置顶别人的评论，每个帖子最多一条） */
    @PutMapping("/comments/{id}/pin")
    @PreAuthorize("!hasRole('STAFF')")
    public Result<CommentVO> pinComment(@PathVariable Long id,
                                        @RequestParam(defaultValue = "true") boolean pinned) {
        return Result.ok(treeholeService.pinComment(SecurityUtil.getCurrentUserId(), id, pinned));
    }
}
