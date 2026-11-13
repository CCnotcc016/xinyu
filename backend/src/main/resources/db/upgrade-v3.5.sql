-- 心语 v3.5 增量升级脚本（幂等，可重复执行）
--
-- 作用：心理自评量表（PHQ-9 / GAD-7 / PHQ-4）的结果存档。
--
-- 设计取舍：
--   · 量表题目本身写在代码里（AssessmentScaleRegistry），不进数据库。
--     题目是国际通用版本，改动必须跟着评分规则一起改，分开存反而容易对不上版本。
--   · 只落库「答案 + 得分 + 严重程度」，不落库原始文本 —— 量表里没有自由输入。
--   · ai_interpretation 是模型生成的解读文本，属于参考内容，前端始终会配上免责提示。
--
-- 开发环境由 Spring Boot 启动时自动执行（见 application-dev.yml 的 schema-locations）；
-- 生产环境请手动执行一次：
--   mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v3.5.sql

CREATE TABLE IF NOT EXISTS `assessment` (
    `id`                BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`           BIGINT      NOT NULL COMMENT '作答用户',
    `scale_key`         VARCHAR(32) NOT NULL COMMENT '量表标识：phq9 / gad7 / phq4',
    `scale_name`        VARCHAR(64) NOT NULL COMMENT '量表名称快照，便于历史记录展示',
    `answers`           VARCHAR(64) NOT NULL COMMENT '逐题得分，逗号分隔，例如 1,2,0,3',
    `total_score`       INT         NOT NULL COMMENT '总分',
    `max_score`         INT         NOT NULL COMMENT '该量表满分',
    `severity`          VARCHAR(24) NOT NULL COMMENT '严重程度代码：NONE / MILD / MODERATE / MODERATELY_SEVERE / SEVERE',
    `risk_flag`         TINYINT     NOT NULL DEFAULT 0 COMMENT '是否出现自伤/自杀念头（PHQ-9 第 9 题 > 0）',
    `ai_interpretation` TEXT        DEFAULT NULL COMMENT 'AI 解读文本（仅供参考，不是诊断）',
    `ai_source`         VARCHAR(8)  DEFAULT NULL COMMENT 'AI / MOCK',
    `created_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `created_at`),
    KEY `idx_user_scale` (`user_id`, `scale_key`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='心理自评量表作答记录';
