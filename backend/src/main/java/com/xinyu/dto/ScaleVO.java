package com.xinyu.dto;

import com.xinyu.service.AssessmentScaleRegistry;
import lombok.Data;

import java.util.List;

/**
 * 量表的题目定义，前端据此渲染答题页。
 *
 * <p>题目下发到前端而不是写死在前端，是为了以后换题库 / 调阈值只改后端一处。</p>
 */
@Data
public class ScaleVO {

    private String key;
    private String name;
    private String shortName;
    private String subtitle;
    private String instruction;
    private List<String> items;
    private List<OptionVO> options;
    private Integer questionCount;
    private Integer maxScore;
    /** 量表出处，例如 PHQ-9（Patient Health Questionnaire-9） */
    private String source;
    /** 页面必须原样展示的免责声明 */
    private String disclaimer;

    @Data
    public static class OptionVO {
        private String label;
        private Integer value;

        public static OptionVO from(AssessmentScaleRegistry.Option o) {
            OptionVO vo = new OptionVO();
            vo.label = o.label();
            vo.value = o.value();
            return vo;
        }
    }

    public static ScaleVO from(AssessmentScaleRegistry.Scale s) {
        ScaleVO vo = new ScaleVO();
        vo.key = s.key();
        vo.name = s.name();
        vo.shortName = s.shortName();
        vo.subtitle = s.subtitle();
        vo.instruction = s.instruction();
        vo.items = s.items();
        vo.options = s.options().stream().map(OptionVO::from).toList();
        vo.questionCount = s.items().size();
        vo.maxScore = s.maxScore();
        vo.source = s.source();
        vo.disclaimer = AssessmentScaleRegistry.DISCLAIMER;
        return vo;
    }
}
