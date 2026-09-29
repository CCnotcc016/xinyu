// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.ai.AiService;
import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.common.PageResult;
import com.xinyu.dto.DiaryRequest;
import com.xinyu.dto.DiaryVO;
import com.xinyu.dto.DistributionVO;
import com.xinyu.dto.TrendPoint;
import com.xinyu.entity.CrisisAlert;
import com.xinyu.entity.Diary;
import com.xinyu.entity.DiaryTag;
import com.xinyu.entity.Tag;
import com.xinyu.mapper.DiaryMapper;
import com.xinyu.mapper.DiaryTagMapper;
import com.xinyu.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    private final DiaryMapper diaryMapper;
    private final DiaryTagMapper diaryTagMapper;
    private final TagMapper tagMapper;
    private final AiService aiService;
    private final RateLimitService rateLimitService;
    private final StatsService statsService;
    private final CrisisSupportService crisisSupportService;
    private final CrisisAlertService crisisAlertService;

    @Transactional
    public DiaryVO create(Long userId, DiaryRequest req) {
        Diary diary = new Diary();
        diary.setUserId(userId);
        diary.setContent(req.getContent());
        diary.setWeather(req.getWeather());
        diary.setScene(req.getScene());
        diary.setPrivacy(normalizePrivacy(req.getPrivacy()));
        diary.setAiStatus("PENDING");
        diaryMapper.insert(diary);
        saveEmotionTags(diary.getId(), req.getEmotionTags());
        DiaryVO vo = toVO(diary);
        // 危机表达在这里就记录：日记是私密的，用户不一定还会去点「生成 AI 回复」，
        // 只挂在 SSE 那一步的话管理员可能永远收不到预警。
        noteCrisis(userId, diary.getId(), req.getContent());
        return vo;
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
        DiaryVO vo = toVO(diary);
        noteCrisis(userId, id, req.getContent());
        return vo;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Diary diary = requireDiary(id);
        if (!diary.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        diaryMapper.deleteById(id);
        diaryTagMapper.delete(new LambdaQueryWrapper<DiaryTag>().eq(DiaryTag::getDiaryId, id));
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

    public List<TrendPoint> trend(Long userId, String range) {
        return statsService.trend(userId, range);
    }

    public DistributionVO distribution(Long userId) {
        return statsService.distribution(userId);
    }

    /** 命中危机词时给管理员记一条预警（前端弹不弹关怀提示由 VO 上的 crisisSupport 决定） */
    private void noteCrisis(Long userId, Long diaryId, String content) {
        if (!crisisSupportService.containsCrisis(content)) {
            return;
        }
        crisisAlertService.record(userId, CrisisAlert.TARGET_DIARY, diaryId, content);
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
        vo.setCrisisSupport(crisisSupportService.containsCrisis(diary.getContent()));
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
