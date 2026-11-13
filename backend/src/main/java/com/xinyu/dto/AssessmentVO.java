package com.xinyu.dto;

import com.xinyu.entity.Assessment;
import com.xinyu.service.AssessmentScaleRegistry;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 一次自评的展示对象：得分 + 严重程度 + 建议 + AI 解读 + 免责声明。
 *
 * <p>前端不自己算分，也不自己拼文案 —— 阈值和话术都在后端，避免两边不一致。</p>
 */
@Data
public class AssessmentVO {

    private Long id;
    private String scaleKey;
    private String scaleName;
    private Integer totalScore;
    private Integer maxScore;
    /** NONE / MILD / MODERATE / MODERATELY_SEVERE / SEVERE */
    private String severity;
    /** 中文描述，例如「中度抑郁症状」 */
    private String severityLabel;
    /** 该档位的固定建议（不依赖模型，模型不可用时也要能看到） */
    private String advice;
    /** PHQ-9 第 9 题为阳性：前端必须弹危机提示 */
    private Boolean riskFlag;
    /** 子维度得分，只有 PHQ-4 有 */
    private Map<String, Integer> dimensions;
    private Integer dimensionMax;
    /** 逐题明细 */
    private List<ItemVO> breakdown;
    private String aiInterpretation;
    /** AI / MOCK */
    private String aiSource;
    private LocalDateTime createdAt;
    private String disclaimer;
    /** 风险阳性时的求助引导，正常为 null */
    private String crisisHint;

    @Data
    public static class ItemVO {
        /** 从 1 开始 */
        private Integer index;
        private String question;
        private Integer score;
        private String answerLabel;
    }

    public static AssessmentVO from(Assessment a) {
        AssessmentScaleRegistry.Scale scale = AssessmentScaleRegistry.find(a.getScaleKey());
        AssessmentVO vo = new AssessmentVO();
        vo.id = a.getId();
        vo.scaleKey = a.getScaleKey();
        vo.scaleName = a.getScaleName();
        vo.totalScore = a.getTotalScore();
        vo.maxScore = a.getMaxScore();
        vo.severity = a.getSeverity();
        vo.riskFlag = Boolean.TRUE.equals(a.getRiskFlag());
        vo.aiInterpretation = a.getAiInterpretation();
        vo.aiSource = a.getAiSource();
        vo.createdAt = a.getCreatedAt();
        vo.disclaimer = AssessmentScaleRegistry.DISCLAIMER;
        vo.crisisHint = vo.riskFlag ? AssessmentScaleRegistry.CRISIS_HINT : null;

        if (scale == null) {
            // 题库已下架的老记录：只回显得分，不装作还能解释
            vo.severityLabel = a.getSeverity();
            return vo;
        }

        List<Integer> answers = parseAnswers(a.getAnswers());
        AssessmentScaleRegistry.Score score = AssessmentScaleRegistry.score(scale, answers);
        vo.severityLabel = score.band().label();
        vo.advice = score.band().advice();
        vo.dimensions = score.dimensions();
        vo.dimensionMax = AssessmentScaleRegistry.dimensionMax(scale);

        List<ItemVO> items = new ArrayList<>();
        for (int i = 0; i < scale.items().size() && i < answers.size(); i++) {
            ItemVO item = new ItemVO();
            item.index = i + 1;
            item.question = scale.items().get(i);
            item.score = answers.get(i);
            item.answerLabel = labelOf(scale, answers.get(i));
            items.add(item);
        }
        vo.breakdown = items;
        return vo;
    }

    /** 历史列表用的精简版：不带逐题明细，省流量 */
    public static AssessmentVO brief(Assessment a) {
        AssessmentVO vo = from(a);
        vo.breakdown = null;
        return vo;
    }

    private static String labelOf(AssessmentScaleRegistry.Scale scale, Integer score) {
        if (score == null) {
            return null;
        }
        for (AssessmentScaleRegistry.Option o : scale.options()) {
            if (o.value() == score) {
                return o.label();
            }
        }
        return null;
    }

    public static List<Integer> parseAnswers(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Integer.valueOf(s);
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .toList();
    }
}
