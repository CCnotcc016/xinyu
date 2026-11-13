package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 心理自评量表的一次作答。
 *
 * <p>量表题目本身放在 {@code AssessmentScaleRegistry} 里，这里只存结果：
 * 逐题得分、总分、严重程度，以及模型生成的解读文本。</p>
 *
 * <p>它不是病历，也不构成诊断依据：前端每次展示都会带上「仅供参考，如有不适请前往线下医院就医」。</p>
 */
@Data
@TableName("assessment")
public class Assessment {

    public static final String SOURCE_AI = "AI";
    public static final String SOURCE_MOCK = "MOCK";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** phq9 / gad7 / phq4 */
    private String scaleKey;
    /** 量表名称快照，避免以后改名字把历史记录一起改掉 */
    private String scaleName;
    /** 逐题得分，逗号分隔 */
    private String answers;
    private Integer totalScore;
    private Integer maxScore;
    /** NONE / MILD / MODERATE / MODERATELY_SEVERE / SEVERE */
    private String severity;
    /** 是否出现自伤 / 自杀念头 */
    private Boolean riskFlag;
    private String aiInterpretation;
    private String aiSource;
    private LocalDateTime createdAt;
}
