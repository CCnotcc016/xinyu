package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.ai.AiService;
import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.common.PageResult;
import com.xinyu.dto.CalendarVO;
import com.xinyu.dto.DiaryRequest;
import com.xinyu.dto.DiaryVO;
import com.xinyu.dto.DistributionVO;
import com.xinyu.dto.EmotionInsightVO;
import com.xinyu.dto.RecapVO;
import com.xinyu.dto.TrendPoint;
import com.xinyu.dto.WeeklyReportVO;
import com.xinyu.entity.CrisisAlert;
import com.xinyu.entity.Diary;
import com.xinyu.entity.DiaryTag;
import com.xinyu.entity.Tag;
import com.xinyu.mapper.DiaryMapper;
import com.xinyu.mapper.DiaryTagMapper;
import com.xinyu.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 日记服务
 */
@Service
@RequiredArgsConstructor
public class DiaryService {

    private static final List<String> PRIVACY_LEVELS = List.of("PRIVATE", "PUBLIC");
    /** 回收站保留天数 */
    private static final int TRASH_RETENTION_DAYS = 7;

    private final DiaryMapper diaryMapper;
    private final DiaryTagMapper diaryTagMapper;
    private final TagMapper tagMapper;
    private final AiService aiService;
    private final RateLimitService rateLimitService;
    private final StatsService statsService;
    private final WeeklyReportService weeklyReportService;
    private final CrisisSupportService crisisSupportService;
    private final CrisisAlertService crisisAlertService;

    @Transactional
    public DiaryVO create(Long userId, DiaryRequest req) {
        // 离线补传：App 没网时先本地存一份，联网后带着同一个 client_uid 重传。
        // 重传可能来自「超时重试」或用户手动点同步，先查一次已存的那条，避免写成两篇。
        String clientUid = StringUtils.hasText(req.getClientUid()) ? req.getClientUid().trim() : null;
        if (clientUid != null) {
            Diary existed = diaryMapper.selectByClientUid(userId, clientUid);
            if (existed != null) {
                return toVO(existed);
            }
        }
        Diary diary = new Diary();
        diary.setUserId(userId);
        diary.setContent(req.getContent());
        diary.setWeather(req.getWeather());
        diary.setScene(req.getScene());
        diary.setPrivacy(normalizePrivacy(req.getPrivacy()));
        diary.setAiStatus("PENDING");
        diary.setClientUid(clientUid);
        try {
            diaryMapper.insert(diary);
        } catch (DuplicateKeyException e) {
            // 两个请求几乎同时到达（例如重试和手动同步撞上）：唯一索引兜底，
            // 直接把先落库的那条返回给调用方，客户端会认为同步成功
            Diary existed = clientUid == null ? null : diaryMapper.selectByClientUid(userId, clientUid);
            if (existed != null) {
                return toVO(existed);
            }
            throw e;
        }
        saveEmotionTags(diary.getId(), req.getEmotionTags());
        applyCrisisReply(diary);
        return toVO(diary);
    }

    public PageResult<DiaryVO> page(Long userId, long page, long size, LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<Diary> wrapper = new LambdaQueryWrapper<Diary>()
                .eq(Diary::getUserId, userId)
                .ge(start != null, Diary::getCreatedAt, start)
                .le(end != null, Diary::getCreatedAt, end)
                .orderByDesc(Diary::getCreatedAt);
        Page<Diary> result = diaryMapper.selectPage(new Page<>(page, size), wrapper);
        Map<Long, List<String>> tagMap = loadEmotionTagsBatch(
                result.getRecords().stream().map(Diary::getId).toList());
        List<DiaryVO> records = result.getRecords().stream()
                .map(d -> toVO(d, tagMap.getOrDefault(d.getId(), Collections.emptyList())))
                .toList();
        return PageResult.of(result.getTotal(), page, size, records);
    }

    public DiaryVO get(Long userId, Long id) {
        Diary diary = requireDiary(id);
        if (!diary.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return toVO(diary);
    }

    @Transactional
    public DiaryVO update(Long userId, Long id, DiaryRequest req) {
        Diary diary = requireDiary(id);
        if (!diary.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        diary.setContent(req.getContent());
        diary.setWeather(req.getWeather());
        diary.setScene(req.getScene());
        diary.setPrivacy(normalizePrivacy(req.getPrivacy()));
        diaryMapper.updateById(diary);

        diaryTagMapper.delete(new LambdaQueryWrapper<DiaryTag>().eq(DiaryTag::getDiaryId, id));
        saveEmotionTags(id, req.getEmotionTags());
        applyCrisisReply(diary);
        return toVO(diary);
    }

    /**
     * 删除进回收站。tags 保留不动，恢复之后情绪标签还在。
     * 逻辑删除由 Diary 上的 @TableLogic 完成（UPDATE deleted_at = NOW()）。
     */
    @Transactional
    public void delete(Long userId, Long id) {
        Diary diary = requireDiary(id);
        if (!diary.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        diaryMapper.deleteById(id);
    }

    /** 回收站列表；顺手清掉超过保留期的记录 */
    public List<DiaryVO> trash(Long userId) {
        purgeExpired(userId);
        List<Diary> list = diaryMapper.selectTrash(userId);
        Map<Long, List<String>> tagMap = loadEmotionTagsBatch(
                list.stream().map(Diary::getId).toList());
        return list.stream()
                .map(d -> toVO(d, tagMap.getOrDefault(d.getId(), Collections.emptyList())))
                .toList();
    }

    @Transactional
    public DiaryVO restore(Long userId, Long id) {
        Diary diary = requireTrashed(userId, id);
        diaryMapper.restoreById(id);
        diary.setDeletedAt(null);
        return toVO(diary);
    }

    /** 永久删除：回收站里点「彻底删除」，或超过保留期自动清理 */
    @Transactional
    public void purge(Long userId, Long id) {
        requireTrashed(userId, id);
        diaryMapper.purgeById(id);
        diaryTagMapper.delete(new LambdaQueryWrapper<DiaryTag>().eq(DiaryTag::getDiaryId, id));
    }

    private Diary requireTrashed(Long userId, Long id) {
        Diary diary = diaryMapper.selectTrashed(id);
        if (diary == null) {
            throw new BusinessException(ErrorCode.DIARY_NOT_FOUND);
        }
        if (!diary.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return diary;
    }

    private void purgeExpired(Long userId) {
        LocalDateTime before = LocalDateTime.now().minusDays(TRASH_RETENTION_DAYS);
        for (Long id : diaryMapper.selectExpiredIds(userId, before)) {
            diaryMapper.purgeById(id);
            diaryTagMapper.delete(new LambdaQueryWrapper<DiaryTag>().eq(DiaryTag::getDiaryId, id));
        }
    }

    public SseEmitter aiReplyStream(Long userId, Long id) {
        Diary diary = requireDiary(id);
        if (!diary.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!rateLimitService.checkAiDaily(userId)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        aiService.streamAnalyze(diary, emitter);
        return emitter;
    }

    public List<TrendPoint> trend(Long userId, String range, LocalDate end) {
        return statsService.trend(userId, range, end);
    }

    public DistributionVO distribution(Long userId) {
        return statsService.distribution(userId);
    }

    public EmotionInsightVO insights(Long userId) {
        return statsService.insights(userId);
    }

    public CalendarVO calendar(Long userId, int days) {
        return statsService.calendar(userId, days);
    }

    public RecapVO recap(Long userId, String period, LocalDate anchor) {
        return statsService.recap(userId, period, anchor);
    }

    public WeeklyReportVO weeklyReport(Long userId, LocalDate weekStart, boolean refresh) {
        return weeklyReportService.weekly(userId, weekStart, refresh);
    }

    /**
     * 命中危机词时立刻把危机干预回复写进日记，并给管理员记一条预警。
     * 日记是私密的，用户很可能不会再去点「生成 AI 回复」——只挂在 SSE 那一步的话，
     * 关怀弹窗关掉之后详情页什么都没有，干预文案等于没显示出来。
     */
    private void applyCrisisReply(Diary diary) {
        if (!crisisSupportService.containsCrisis(diary.getContent())) {
            return;
        }
        if (!"CRISIS".equals(diary.getAiStatus())) {
            diary.setAiStatus("CRISIS");
            diary.setAiReply(CrisisSupportService.CRISIS_REPLY);
            diaryMapper.updateById(diary);
        }
        crisisAlertService.record(diary.getUserId(), CrisisAlert.TARGET_DIARY,
                diary.getId(), diary.getContent());
    }

    private Diary requireDiary(Long id) {
        Diary diary = diaryMapper.selectById(id);
        if (diary == null) {
            throw new BusinessException(ErrorCode.DIARY_NOT_FOUND);
        }
        return diary;
    }

    private String normalizePrivacy(String privacy) {
        String p = privacy == null ? "PRIVATE" : privacy.toUpperCase();
        return PRIVACY_LEVELS.contains(p) ? p : "PRIVATE";
    }

    private void saveEmotionTags(Long diaryId, List<String> emotionTags) {
        if (emotionTags == null || emotionTags.isEmpty()) {
            return;
        }
        for (String name : emotionTags.stream().distinct().toList()) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                    .eq(Tag::getName, name.trim())
                    .eq(Tag::getType, "EMOTION"));
            if (tag == null) {
                tag = new Tag();
                tag.setName(name.trim());
                tag.setType("EMOTION");
                tagMapper.insert(tag);
            }
            DiaryTag dt = new DiaryTag();
            dt.setDiaryId(diaryId);
            dt.setTagId(tag.getId());
            diaryTagMapper.insert(dt);
        }
    }

    private List<String> loadEmotionTags(Long diaryId) {
        List<DiaryTag> links = diaryTagMapper.selectList(
                new LambdaQueryWrapper<DiaryTag>().eq(DiaryTag::getDiaryId, diaryId));
        if (links.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> tagIds = links.stream().map(DiaryTag::getTagId).toList();
        return tagMapper.selectBatchIds(tagIds).stream()
                .filter(t -> "EMOTION".equals(t.getType()))
                .map(Tag::getName)
                .toList();
    }

    private DiaryVO toVO(Diary diary) {
        return toVO(diary, loadEmotionTags(diary.getId()));
    }

    private DiaryVO toVO(Diary diary, List<String> emotionTags) {
        DiaryVO vo = DiaryVO.from(diary, emotionTags);
        // 详情 / 列表也要带上关怀标记，用户重新翻到这篇日记时同样能看到提示
        boolean crisis = crisisSupportService.containsCrisis(diary.getContent());
        vo.setCrisisSupport(crisis);
        // 兜底：老数据（命中了危机词但当时还没落库干预文案）也要能把干预内容显示出来，
        // 否则详情页只有一句「让 AI 陪你说说话」，等于关怀提示没出现
        if (crisis && (diary.getAiReply() == null || diary.getAiReply().isBlank())) {
            vo.setAiStatus("CRISIS");
            vo.setAiReply(CrisisSupportService.CRISIS_REPLY);
        }
        return vo;
    }

    /** 整页日记一次查完标签，替代逐条 loadEmotionTags（原来每条日记 2 次查询） */
    private Map<Long, List<String>> loadEmotionTagsBatch(List<Long> diaryIds) {
        if (diaryIds == null || diaryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DiaryTag> links = diaryTagMapper.selectList(
                new LambdaQueryWrapper<DiaryTag>().in(DiaryTag::getDiaryId, diaryIds));
        if (links.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> tagNames = tagMapper.selectBatchIds(
                        links.stream().map(DiaryTag::getTagId).distinct().toList()).stream()
                .filter(t -> "EMOTION".equals(t.getType()))
                .collect(Collectors.toMap(Tag::getId, Tag::getName));
        Map<Long, List<String>> grouped = new HashMap<>();
        for (DiaryTag link : links) {
            String name = tagNames.get(link.getTagId());
            if (name != null) {
                grouped.computeIfAbsent(link.getDiaryId(), k -> new ArrayList<>()).add(name);
            }
        }
        return grouped;
    }
}
