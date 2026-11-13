package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.common.PageResult;
import com.xinyu.dto.CrisisAlertHandleRequest;
import com.xinyu.dto.CrisisAlertVO;
import com.xinyu.dto.CrisisFollowUpRequest;
import com.xinyu.entity.CrisisAlert;
import com.xinyu.entity.User;
import com.xinyu.mapper.CrisisAlertMapper;
import com.xinyu.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 危机预警服务。
 *
 * <p>用户在树洞或日记里表达自残 / 自杀倾向时：</p>
 * <ol>
 *   <li>内容照常发布（不删、不拦，拦了只会把人推得更远），前端弹关怀弹窗给热线；</li>
 *   <li>后端同时落一条预警记录，管理员的「危机预警」页能看到，
 *       并通过注册手机号尝试联系本人。</li>
 * </ol>
 *
 * <p>不是诊断，也不是监控：只记录关键词触发的那一条，正文只留摘要。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrisisAlertService {

    /** 摘要长度：够管理员判断是否需要介入即可，不做全文存档 */
    private static final int EXCERPT_LENGTH = 500;

    private static final Set<String> HANDLE_STATUSES =
            Set.of(CrisisAlert.STATUS_HANDLED, CrisisAlert.STATUS_DISMISSED);

    private final CrisisAlertMapper alertMapper;
    private final UserMapper userMapper;
    private final CrisisSupportService crisisSupportService;

    /**
     * 命中危机关键词就记一条预警。没命中什么都不做，调用方不用先判断。
     */
    public void record(Long userId, String targetType, Long targetId, String content) {
        String keyword = crisisSupportService.matchedKeyword(content);
        if (keyword == null || userId == null) {
            return;
        }
        try {
            String excerpt = excerpt(content);
            // 同一条内容重复提交（改日记 / 编辑帖子）时更新原记录，不刷屏
            CrisisAlert exists = alertMapper.selectOne(new LambdaQueryWrapper<CrisisAlert>()
                    .eq(CrisisAlert::getTargetType, targetType)
                    .eq(CrisisAlert::getTargetId, targetId)
                    .eq(CrisisAlert::getStatus, CrisisAlert.STATUS_PENDING)
                    .orderByDesc(CrisisAlert::getId)
                    .last("LIMIT 1"));
            if (exists != null) {
                exists.setKeyword(keyword);
                exists.setContent(excerpt);
                alertMapper.updateById(exists);
                return;
            }
            CrisisAlert alert = new CrisisAlert();
            alert.setUserId(userId);
            alert.setTargetType(targetType);
            alert.setTargetId(targetId);
            alert.setKeyword(keyword);
            alert.setContent(excerpt);
            alert.setStatus(CrisisAlert.STATUS_PENDING);
            alertMapper.insert(alert);
            log.warn("危机预警：user={} target={}#{} 命中关键词「{}」", userId, targetType, targetId, keyword);
        } catch (Exception e) {
            // 预警是旁路逻辑，落库失败绝不能连累用户正常发帖 / 写日记
            log.error("危机预警记录失败: {}", e.getMessage());
        }
    }

    /**
     * 直接记一条预警，不经过关键词判断。
     *
     * <p>心理自评第 9 题（自伤 / 自杀念头）选到非零时用它：量表里没有自由文本，
     * 关键词匹配不上，但这一题的阳性本身就是需要跟进的信息。</p>
     */
    public void recordRisk(Long userId, String targetType, Long targetId, String reason, String content) {
        if (userId == null || targetId == null) {
            return;
        }
        try {
            CrisisAlert alert = new CrisisAlert();
            alert.setUserId(userId);
            alert.setTargetType(targetType);
            alert.setTargetId(targetId);
            alert.setKeyword(reason);
            alert.setContent(excerpt(content));
            alert.setStatus(CrisisAlert.STATUS_PENDING);
            alertMapper.insert(alert);
            log.warn("危机预警：user={} target={}#{} 触发 {}", userId, targetType, targetId, reason);
        } catch (Exception e) {
            log.error("危机预警记录失败: {}", e.getMessage());
        }
    }

    public PageResult<CrisisAlertVO> list(String status, long page, long size) {
        Page<CrisisAlert> result = alertMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<CrisisAlert>()
                        .eq(StringUtils.hasText(status), CrisisAlert::getStatus, status)
                        .orderByDesc(CrisisAlert::getStatus)
                        .orderByDesc(CrisisAlert::getCreatedAt));
        Map<Long, User> users = users(result.getRecords().stream()
                .map(CrisisAlert::getUserId).distinct().toList());
        // 处理人 / 回访人单独查一次：预警的 user_id 是「被关心的人」，不是处理人
        List<Long> staffIds = new java.util.ArrayList<>();
        for (CrisisAlert alert : result.getRecords()) {
            if (alert.getHandledBy() != null) {
                staffIds.add(alert.getHandledBy());
            }
            if (alert.getFollowUpBy() != null) {
                staffIds.add(alert.getFollowUpBy());
            }
        }
        Map<Long, User> staff = users(staffIds.stream().distinct().toList());
        List<CrisisAlertVO> records = result.getRecords().stream()
                .map(a -> toVO(a, pick(users, a.getUserId()),
                        pick(staff, a.getHandledBy()), pick(staff, a.getFollowUpBy())))
                .toList();
        return PageResult.of(result.getTotal(), page, size, records);
    }

    /** 管理员端红点用 */
    public long pendingCount() {
        return alertMapper.selectCount(new LambdaQueryWrapper<CrisisAlert>()
                .eq(CrisisAlert::getStatus, CrisisAlert.STATUS_PENDING));
    }

    @Transactional(rollbackFor = Exception.class)
    public void handle(Long adminId, Long id, CrisisAlertHandleRequest req) {
        CrisisAlert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw new BusinessException(ErrorCode.CRISIS_ALERT_NOT_FOUND);
        }
        String status = req.getStatus() == null ? "" : req.getStatus().trim().toUpperCase();
        if (!HANDLE_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        alert.setStatus(status);
        alert.setRemark(req.getRemark());
        alert.setHandledBy(adminId);
        alert.setHandledAt(LocalDateTime.now());
        alertMapper.updateById(alert);
    }

    /**
     * 回访：处理完之后再记一次「联系上了吗、状态如何」。
     *
     * <p>只允许对已处理的预警回访 —— 待跟进的本来就还没接手，
     * 先处理、再回访，顺序反了会让跟进记录失去意义。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void followUp(Long adminId, Long id, CrisisFollowUpRequest req) {
        CrisisAlert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw new BusinessException(ErrorCode.CRISIS_ALERT_NOT_FOUND);
        }
        if (CrisisAlert.STATUS_PENDING.equals(alert.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        alert.setFollowUpAt(LocalDateTime.now());
        alert.setFollowUpNote(req.getNote() == null ? null : req.getNote().trim());
        alert.setFollowUpBy(adminId);
        alertMapper.updateById(alert);
    }

    private CrisisAlertVO toVO(CrisisAlert alert, User user, User handler, User followUpBy) {
        CrisisAlertVO vo = new CrisisAlertVO();
        vo.setId(alert.getId());
        vo.setUserId(alert.getUserId());
        vo.setTargetType(alert.getTargetType());
        vo.setTargetId(alert.getTargetId());
        vo.setKeyword(alert.getKeyword());
        vo.setContent(alert.getContent());
        vo.setStatus(alert.getStatus());
        vo.setRemark(alert.getRemark());
        if (handler != null) {
            vo.setHandledByName(handler.getNickname() != null && !handler.getNickname().isBlank()
                    ? handler.getNickname() : handler.getUsername());
        }
        vo.setHandledAt(alert.getHandledAt());
        vo.setFollowUpAt(alert.getFollowUpAt());
        vo.setFollowUpNote(alert.getFollowUpNote());
        if (followUpBy != null) {
            vo.setFollowUpByName(StringUtils.hasText(followUpBy.getNickname())
                    ? followUpBy.getNickname() : followUpBy.getUsername());
        }
        vo.setCreatedAt(alert.getCreatedAt());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            // 明文手机号：仅此接口，仅管理员，用于紧急联系
            vo.setPhone(user.getPhone());
        }
        return vo;
    }

    private Map<Long, User> users(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    /** Map.of() 是不可变 Map，get(null) 会直接抛 NPE，所以查表前先挡掉空 key */
    private static User pick(Map<Long, User> map, Long id) {
        return id == null ? null : map.get(id);
    }

    private String excerpt(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        // 列宽是 varchar(500)，省略号也占一位：多出来的这一个字符会让整条预警写不进去，
        // 而写失败是被吞掉的（预警不能影响用户发日记），结果就是长日记静默漏预警。
        return trimmed.length() <= EXCERPT_LENGTH
                ? trimmed
                : trimmed.substring(0, EXCERPT_LENGTH - 1) + "…";
    }
}
