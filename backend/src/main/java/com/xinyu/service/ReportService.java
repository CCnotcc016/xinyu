package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.common.PageResult;
import com.xinyu.dto.ReportRequest;
import com.xinyu.dto.ReportVO;
import com.xinyu.entity.PostComment;
import com.xinyu.entity.Report;
import com.xinyu.entity.TreeholePost;
import com.xinyu.entity.User;
import com.xinyu.mapper.PostCommentMapper;
import com.xinyu.mapper.ReportMapper;
import com.xinyu.mapper.TreeholePostMapper;
import com.xinyu.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 举报服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final List<String> TARGET_TYPES = List.of("POST", "COMMENT", "USER");

    private final ReportMapper reportMapper;
    private final TreeholePostMapper postMapper;
    private final PostCommentMapper commentMapper;
    private final UserMapper userMapper;

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

    /**
     * 我发起的举报 + 处理结果。
     *
     * <p>只给举报人看自己那部分：不暴露处理人、不暴露其他人的举报数量。</p>
     */
    public PageResult<ReportVO> mine(Long userId, long page, long size) {
        Page<Report> result = reportMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getReporterId, userId)
                        .orderByDesc(Report::getCreatedAt));
        Map<String, String> contents = targetContents(result.getRecords());
        List<ReportVO> records = result.getRecords().stream()
                .map(r -> ReportVO.from(r, null,
                        contents.getOrDefault(key(r.getTargetType(), r.getTargetId()), "(已删除)")))
                .toList();
        return PageResult.of(result.getTotal(), page, size, records);
    }

    /** 按目标类型分组批量取内容预览；查询失败降级为空，不影响列表加载 */
    private Map<String, String> targetContents(List<Report> reports) {
        Map<String, String> contents = new HashMap<>();
        Map<String, List<Long>> idsByType = reports.stream()
                .filter(r -> r.getTargetType() != null && r.getTargetId() != null)
                .collect(Collectors.groupingBy(Report::getTargetType,
                        Collectors.mapping(Report::getTargetId, Collectors.toList())));
        try {
            List<Long> postIds = idsByType.getOrDefault("POST", List.of());
            if (!postIds.isEmpty()) {
                postMapper.selectBatchIds(postIds)
                        .forEach(p -> contents.put(key("POST", p.getId()), p.getContent()));
            }
            List<Long> commentIds = idsByType.getOrDefault("COMMENT", List.of());
            if (!commentIds.isEmpty()) {
                commentMapper.selectBatchIds(commentIds)
                        .forEach(c -> contents.put(key("COMMENT", c.getId()), c.getContent()));
            }
            List<Long> userIds = idsByType.getOrDefault("USER", List.of());
            if (!userIds.isEmpty()) {
                userMapper.selectBatchIds(userIds)
                        .forEach(u -> contents.put(key("USER", u.getId()), u.getUsername()));
            }
        } catch (Exception e) {
            log.warn("加载举报目标内容失败", e);
        }
        return contents;
    }

    private static String key(String type, Long id) {
        return type + "#" + id;
    }
}
