package com.xinyu.service;

import java.util.List;
import java.util.Map;

/**
 * 心理自评量表的题库与评分规则。
 *
 * <p>三个量表都是国际通用的公开筛查工具，题目与阈值按通行版本写死在这里：</p>
 * <ul>
 *   <li>{@code phq9} —— 抑郁自评（9 题，0-27）；</li>
 *   <li>{@code gad7} —— 焦虑自评（7 题，0-21）；</li>
 *   <li>{@code phq4} —— 抑郁焦虑快速自评（4 题，0-12，抑郁 / 焦虑各 2 题）。</li>
 * </ul>
 *
 * <p>三条底线，改题库时不要破：</p>
 * <ol>
 *   <li>只叫「自评 / 筛查」，不叫「诊断」；结果页永远带 {@link #DISCLAIMER}；</li>
 *   <li>严重程度用「症状提示」描述，不写「你有抑郁症 / 焦虑症」；</li>
 *   <li>中度及以上一律引导到线下医院，不给用药建议。</li>
 * </ol>
 */
public final class AssessmentScaleRegistry {

    public static final String SCALE_PHQ9 = "phq9";
    public static final String SCALE_GAD7 = "gad7";
    public static final String SCALE_PHQ4 = "phq4";

    /** 所有量表页面都要展示的免责声明 */
    public static final String DISCLAIMER =
            "本量表为国际通用的自评筛查工具，结果仅供参考，不构成医学诊断，也不能替代面诊。"
                    + "如有不适请前往线下医院就医。";
    /** 细长条横幅用的短版 */
    public static final String SHORT_DISCLAIMER = "仅供参考，如有不适请前往线下医院就医";

    /** 危机时的求助引导，和日记、树洞共用同一套话术 */
    public static final String CRISIS_HINT =
            "如果此刻已经有伤害自己的念头，请先联系一个信任的人，"
                    + "拨打全国心理援助热线 12356；情况紧急请立即拨打 120 或 110。";

    /** 答案选项：过去两周内出现的频率 */
    public static final List<Option> FREQUENCY_OPTIONS = List.of(
            new Option("完全不会", 0),
            new Option("好几天", 1),
            new Option("一半以上时间", 2),
            new Option("几乎每天", 3));

    /** 严重程度代码，数值越大越需要尽快找专业帮助 */
    public enum Severity {
        NONE, MILD, MODERATE, MODERATELY_SEVERE, SEVERE
    }

    /** 评分区间：同一量表里按总分落到唯一一段 */
    public record Band(Severity severity, String label, int min, int max, String advice) {
    }

    public record Option(String label, int value) {
    }

    /**
     * 一个量表的完整定义。
     *
     * @param dimensions 子维度（如 PHQ-4 的抑郁 / 焦虑），值为题号下标
     */
    public record Scale(String key, String name, String shortName, String subtitle, String instruction,
                        List<String> items, List<Option> options, List<Band> bands,
                        Map<String, List<Integer>> dimensions, String source) {

        public int maxScore() {
            return items.size() * 3;
        }

        /** 命中哪个区间；正常不会为空，兜底返回最后一段 */
        public Band bandOf(int total) {
            for (Band b : bands) {
                if (total >= b.min() && total <= b.max()) {
                    return b;
                }
            }
            return bands.get(bands.size() - 1);
        }
    }

    /** 评分结果 */
    public record Score(int total, int maxScore, Band band, boolean risk,
                        Map<String, Integer> dimensions) {
    }

    /* ------------------------------------------------------------------ */

    private static final List<Band> PHQ9_BANDS = List.of(
            new Band(Severity.NONE, "没有明显抑郁症状", 0, 4,
                    "这次自评没有提示明显症状。继续保持现在的作息和节奏就好，累的时候允许自己停下来。"),
            new Band(Severity.MILD, "轻度抑郁症状", 5, 9,
                    "有一些轻度的困扰，通常可以从规律睡眠、每天出门走一走、和信任的人聊几句开始。"
                            + "如果持续两周以上没有好转，建议去线下医院精神科或心理科做一次专业评估。"),
            new Band(Severity.MODERATE, "中度抑郁症状", 10, 14,
                    "困扰已经比较明显，靠自己硬扛往往更累。建议近期到线下医院精神科或心理科就诊，"
                            + "让医生做一次面对面评估。"),
            new Band(Severity.MODERATELY_SEVERE, "中重度抑郁症状", 15, 19,
                    "这个分数提示症状偏重，已经明显影响生活的可能性较大。建议尽快前往线下医院精神科就诊，"
                            + "也可以先和信任的人说一声，让身边有人知道你的状态。"),
            new Band(Severity.SEVERE, "重度抑郁症状", 20, 27,
                    "这个分数提示症状较重。请尽快前往线下医院精神科就诊，最好有人陪同。"
                            + "如果出现伤害自己的念头，请立即联系信任的人或拨打 12356，紧急时拨打 120 / 110。"));

    private static final List<Band> GAD7_BANDS = List.of(
            new Band(Severity.NONE, "没有明显焦虑症状", 0, 4,
                    "这次自评没有提示明显症状。保持规律作息，紧张的时候做几次深呼吸就够了。"),
            new Band(Severity.MILD, "轻度焦虑症状", 5, 9,
                    "有一些轻度的紧张和担忧，通常可以通过减少咖啡因、规律运动、把担心的事写下来缓解。"
                            + "如果持续两周以上没有好转，建议去线下医院心理科做一次专业评估。"),
            new Band(Severity.MODERATE, "中度焦虑症状", 10, 14,
                    "焦虑已经比较明显，可能开始影响睡眠或注意力。建议近期到线下医院心理科或精神科就诊。"),
            new Band(Severity.SEVERE, "重度焦虑症状", 15, 21,
                    "这个分数提示焦虑程度较重。建议尽快前往线下医院精神科就诊；"
                            + "如果出现明显的躯体不适（心慌、胸闷、无法呼吸），请及时就医。"));

    private static final List<Band> PHQ4_BANDS = List.of(
            new Band(Severity.NONE, "没有明显抑郁焦虑症状", 0, 2,
                    "这次快速自评没有提示明显症状，保持现在的节奏就好。"),
            new Band(Severity.MILD, "轻度抑郁焦虑症状", 3, 5,
                    "有一些轻度的情绪困扰，可以先从规律睡眠、适度运动、和信任的人聊聊开始。"
                            + "如果两周以上没有缓解，建议去线下医院心理科做一次专业评估。"),
            new Band(Severity.MODERATE, "中度抑郁焦虑症状", 6, 8,
                    "困扰已经比较明显。建议近期到线下医院精神科或心理科就诊，做一次更完整的评估。"),
            new Band(Severity.SEVERE, "重度抑郁焦虑症状", 9, 12,
                    "这个分数提示症状较重。建议尽快前往线下医院精神科就诊，最好有人陪同。"
                            + "如果出现伤害自己的念头，请立即联系信任的人或拨打 12356，紧急时拨打 120 / 110。"));

    public static final Scale PHQ9 = new Scale(
            SCALE_PHQ9,
            "抑郁自评量表（PHQ-9）",
            "抑郁自评",
            "9 道题 · 约 2 分钟 · 看最近两周",
            "在过去两周里，以下问题困扰你的频率是多少？请按真实感受选择，没有对错。",
            List.of(
                    "做事时提不起劲或没有兴趣",
                    "感到心情低落、沮丧或绝望",
                    "入睡困难、睡不安稳或睡得过多",
                    "感觉疲倦或没有活力",
                    "食欲不振，或吃得过多",
                    "觉得自己很糟，或觉得自己很失败，或让自己和家人失望",
                    "对事物专注有困难，例如看报纸或看电视时",
                    "动作或说话速度缓慢到别人已经察觉；或正好相反，烦躁、坐立不安、动来动去比平时更严重",
                    "有不如死掉，或用某种方式伤害自己的念头"),
            FREQUENCY_OPTIONS,
            PHQ9_BANDS,
            Map.of(),
            "PHQ-9（Patient Health Questionnaire-9）");

    public static final Scale GAD7 = new Scale(
            SCALE_GAD7,
            "焦虑自评量表（GAD-7）",
            "焦虑自评",
            "7 道题 · 约 2 分钟 · 看最近两周",
            "在过去两周里，以下问题困扰你的频率是多少？请按真实感受选择，没有对错。",
            List.of(
                    "感到紧张、焦虑或急切",
                    "不能够停止或控制担忧",
                    "对各种各样的事情担忧过多",
                    "很难放松下来",
                    "由于不安而无法静坐",
                    "变得容易烦恼或急躁",
                    "感到似乎将有可怕的事情发生而害怕"),
            FREQUENCY_OPTIONS,
            GAD7_BANDS,
            Map.of(),
            "GAD-7（Generalized Anxiety Disorder-7）");

    public static final Scale PHQ4 = new Scale(
            SCALE_PHQ4,
            "抑郁焦虑快速自评（PHQ-4）",
            "抑郁焦虑",
            "4 道题 · 约 1 分钟 · 快速筛查",
            "在过去两周里，以下问题困扰你的频率是多少？前两题看情绪低落，后两题看紧张担忧。",
            List.of(
                    "感到心情低落、沮丧或绝望",
                    "做事时提不起劲或没有兴趣",
                    "感到紧张、焦虑或急切",
                    "不能够停止或控制担忧"),
            FREQUENCY_OPTIONS,
            PHQ4_BANDS,
            Map.of("抑郁", List.of(0, 1), "焦虑", List.of(2, 3)),
            "PHQ-4（PHQ-2 + GAD-2）");

    private static final List<Scale> ALL = List.of(PHQ9, GAD7, PHQ4);

    private AssessmentScaleRegistry() {
    }

    public static List<Scale> all() {
        return ALL;
    }

    /** 按 key 找量表，找不到返回 null */
    public static Scale find(String key) {
        if (key == null) {
            return null;
        }
        for (Scale s : ALL) {
            if (s.key().equalsIgnoreCase(key)) {
                return s;
            }
        }
        return null;
    }

    /**
     * 计分。答案必须与题目一一对应，越界或负数在调用方先校验掉。
     *
     * <p>风险标记只认一件确定的事：PHQ-9 第 9 题不为 0（出现过自伤 / 自杀念头）。
     * 任何非零都按阳性处理——宁可多提示一次，也不能漏。</p>
     */
    public static Score score(Scale scale, List<Integer> answers) {
        int total = 0;
        for (Integer v : answers) {
            total += v == null ? 0 : v;
        }
        boolean risk = SCALE_PHQ9.equals(scale.key())
                && answers.size() > 8
                && answers.get(8) != null
                && answers.get(8) > 0;

        // 无子维度时给空 Map，避免调用方空指针
        Map<String, Integer> dims = new java.util.LinkedHashMap<>();
        if (!scale.dimensions().isEmpty()) {
            for (Map.Entry<String, List<Integer>> e : scale.dimensions().entrySet()) {
                int sum = 0;
                for (Integer idx : e.getValue()) {
                    Integer v = answers.get(idx);
                    sum += v == null ? 0 : v;
                }
                dims.put(e.getKey(), sum);
            }
        }
        return new Score(total, scale.maxScore(), scale.bandOf(total), risk, dims);
    }

    /** 子维度满分（每题 3 分） */
    public static int dimensionMax(Scale scale) {
        if (scale.dimensions().isEmpty()) {
            return 0;
        }
        return scale.dimensions().values().iterator().next().size() * 3;
    }
}
