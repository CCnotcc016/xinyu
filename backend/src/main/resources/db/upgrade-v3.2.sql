-- 心语 v3.2 增量升级脚本（幂等，可重复执行）
--
-- 作用：补上 v3.2 新增的结构。
--   · crisis_alert.follow_up_* 危机预警的回访记录
--   · sensitive_word           后台可维护的敏感词库
--   · notification.type        支持 REPORT（举报处理结果回执）
--   · diary.deleted_at         日记改为逻辑删除（回收站，7 天内可恢复）
--
-- 沿用 v2.0 起的写法：MySQL 8 不支持列的 IF NOT EXISTS，
-- 所以查 information_schema → 动态拼 SQL，重复执行不会报错。
--
-- 开发环境由 Spring Boot 启动时自动执行（见 application-dev.yml 的 schema-locations）；
-- 生产环境请手动执行一次：
--   mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v3.2.sql

-- 1) diary.deleted_at 回收站
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'diary' AND COLUMN_NAME = 'deleted_at') = 0,
    'ALTER TABLE diary ADD COLUMN deleted_at DATETIME DEFAULT NULL COMMENT ''进回收站的时间，NULL 表示未删除'' AFTER updated_at, ADD KEY idx_user_deleted (user_id, deleted_at)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1b) report.remark 处理说明（举报人能看到结果与说明）
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'report' AND COLUMN_NAME = 'remark') = 0,
    'ALTER TABLE report ADD COLUMN remark VARCHAR(255) DEFAULT NULL COMMENT ''处理说明，举报人可见'' AFTER status',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) crisis_alert 回访字段
SET @has_follow := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crisis_alert' AND COLUMN_NAME = 'follow_up_at'
);
SET @ddl := IF(@has_follow = 0,
    'ALTER TABLE `crisis_alert`
        ADD COLUMN `follow_up_at`   DATETIME     DEFAULT NULL COMMENT ''最近一次回访时间'',
        ADD COLUMN `follow_up_note` VARCHAR(255) DEFAULT NULL COMMENT ''回访情况：是否联系上、状态如何'',
        ADD COLUMN `follow_up_by`   BIGINT       DEFAULT NULL COMMENT ''回访人'' ',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 敏感词库（后台可增删，启动时若为空会自动从 sensitive-words.txt 导入）
CREATE TABLE IF NOT EXISTS `sensitive_word` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `word`       VARCHAR(50)  NOT NULL COMMENT '敏感词，按包含匹配',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_word` (`word`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='敏感词库';

-- 4) 通知类型放宽说明（type 是 varchar(20)，无需改结构，仅同步注释）
ALTER TABLE `notification` MODIFY COLUMN `type` VARCHAR(20) NOT NULL
    COMMENT 'LIKE/COMMENT/COMMENT_REPLY/FRIEND_REQUEST/FRIEND_ACCEPT/CHAT/SUPPORT/REPORT';
