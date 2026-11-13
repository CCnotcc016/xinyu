package com.xinyu.service;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 危机干预支持：识别自残/自杀等危机表达，并给出统一的陪伴引导。
 *
 * <p>注意：危机内容不应被敏感词系统拦截，而应走"陪伴 + 求助引导"的路径，
 * 让用户感受到被理解，同时引导其寻求专业帮助。</p>
 *
 * <p><b>分级（v3.1 安全加固）</b>：旧版本把「死」「太累了」「绝望」「注销」「海边」
 * 这类日常表达也当成危机词，一个字的「死」会让「笑死我了」「死机了」全部命中，
 * 结果是后台预警刷屏、真正的求助被淹掉。现在分两级：</p>
 * <ul>
 *   <li>{@link RiskLevel#HIGH}：明确的意念 / 计划 / 方式 / 告别 —— 弹干预卡片 + 落预警；</li>
 *   <li>{@link RiskLevel#LOW}：绝望、无价值感等弱信号 —— 只给关怀提示，不惊动后台。</li>
 * </ul>
 *
 * <p>另外做否定处理：「不想死」「没有想过自杀」「不会自残」不该判成危机，
 * 但「不想活」本身就是危机表达，所以否定前缀只看关键词之前，不看关键词内部。</p>
 */
@Service
public class CrisisSupportService {

    public enum RiskLevel {
        NONE, LOW, HIGH
    }

    /** 明确的意念 / 计划 / 方式 / 告别：命中即高危 */
    private static final List<String> HIGH_RISK_KEYWORDS = List.of(
            // 一、直接的意念表达
            "自杀", "想自杀", "要自杀", "自杀计划", "轻生", "想轻生", "寻死",
            "想死", "真想死", "想去死", "死了算了", "不如死了", "死了更好",
            "不想活", "不想活了", "不想再活", "活不下去", "没法活下去",
            "结束生命", "想结束生命", "结束自己", "想结束自己", "了结自己",
            "自我了断", "一了百了", "杀了我自己", "弄死自己",
            // 二、自伤 / 自残
            "自残", "想自残", "自伤", "割自己", "划自己", "划手臂", "烫自己",
            "烟头烫自己", "撞墙自残",
            // 三、方式（带明确动作，不做泛化地点词）
            "跳楼", "跳桥", "跳河", "跳轨", "卧轨", "上吊", "自缢", "割腕", "割脉",
            "服毒", "喝农药", "烧炭", "一氧化碳中毒", "吞药自杀", "过量服药", "溺水轻生",
            // 四、计划与告别
            "遗书", "遗言", "交代后事", "安排后事", "最后一条动态", "来生再见",
            "下辈子再见", "永别了", "告别世界", "离开这个世界", "永远睡过去",
            "睡过去不再醒", "不想醒来",
            // 五、明确的网络隐语（只保留无歧义的）
            "自鲨", "紫砂了");

    /** 弱信号：只做关怀提示，不落预警 */
    private static final List<String> LOW_RISK_KEYWORDS = List.of(
            "绝望", "没有希望", "看不到希望", "活着没意思", "活着没意义",
            "撑不下去", "扛不住", "熬不过去", "活着太累",
            "我是负担", "拖累别人", "累赘", "没有价值", "没人需要我",
            "都是我的错", "想结束痛苦", "想结束一切", "想消失", "想逃避一切");

    /** 否定前缀：关键词前面出现这些词时，说明用户是在否认，不能判成危机 */
    private static final List<String> NEGATIONS = List.of(
            "不想", "不会", "不再", "没有", "没", "从不", "从没", "不", "未", "别");

    /** 否定判断的回看窗口：中文否定词一般紧贴关键词，看前 4 个字足够 */
    private static final int NEGATION_WINDOW = 4;

    public static final String CRISIS_REPLY = """
            检测到你可能正处于非常艰难的时刻。你不是一个人，请一定优先照顾自己的安全。

            建议你：
            1. 立即联系你信任的家人或朋友，告诉他们你此刻的感受；
            2. 拨打心理援助热线（全国 12356）寻求专业支持；
            3. 若情况紧急，请立即拨打 120 或 110。

            生命值得被认真对待，请给自己一次被帮助的机会。❤️""";

    /** 风险分级：只做关键词 + 否定前缀的粗判，不是诊断 */
    public RiskLevel assess(String content) {
        if (content == null || content.isEmpty()) {
            return RiskLevel.NONE;
        }
        if (matchedKeyword(content) != null) {
            return RiskLevel.HIGH;
        }
        return matchedIn(content, LOW_RISK_KEYWORDS) != null ? RiskLevel.LOW : RiskLevel.NONE;
    }

    /** 是否命中高危表达（干预文案、后台预警只看这个） */
    public boolean containsCrisis(String content) {
        return assess(content) == RiskLevel.HIGH;
    }

    /** 是否需要展示关怀提示（高危 + 弱信号都给，只是不惊动后台） */
    public boolean needsCare(String content) {
        return assess(content) != RiskLevel.NONE;
    }

    /**
     * 返回命中的第一个高危关键词，未命中返回 null。
     * 预警记录里存下命中的词，管理员一眼就能看出是什么触发的。
     */
    public String matchedKeyword(String content) {
        return matchedIn(content, HIGH_RISK_KEYWORDS);
    }

    private String matchedIn(String content, List<String> keywords) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        for (String kw : keywords) {
            int from = 0;
            while (true) {
                int index = content.indexOf(kw, from);
                if (index < 0) {
                    break;
                }
                if (!negated(content, index)) {
                    return kw;
                }
                from = index + kw.length();
            }
        }
        return null;
    }

    /** 关键词前面紧邻的若干字符里是否出现否定词 */
    private boolean negated(String content, int index) {
        String prefix = content.substring(Math.max(0, index - NEGATION_WINDOW), index);
        for (String negation : NEGATIONS) {
            if (prefix.endsWith(negation)) {
                return true;
            }
        }
        return false;
    }
}
