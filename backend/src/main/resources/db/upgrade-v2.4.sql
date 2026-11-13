-- 心语 v2.4 增量升级脚本（幂等，可重复执行）
--
-- 作用：给已存在的旧库补上 v2.4 需要的结构。
--   · post_comment.pinned_at       树洞评论区置顶（贴主可置顶任意一条评论，NULL = 未置顶）
--   · support_conversation / support_message
--                                 「联系管理员/客服」的会话与消息
--                                 （新建表用 CREATE TABLE IF NOT EXISTS，重复执行无副作用）
--
-- 与 upgrade-v2.sql / v2.2 / v2.3 写法一致：先查 information_schema 再动态拼 DDL，
-- 因为 MySQL 8 不支持 ADD COLUMN IF NOT EXISTS（那是 MariaDB 的语法）。
--
-- 开发环境由 Spring Boot 启动时自动执行（见 application-dev.yml 的 schema-locations）；
-- 生产环境请手动执行一次：
--   mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v2.4.sql

-- 1) post_comment.pinned_at
SET @has_pinned_at := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post_comment' AND COLUMN_NAME = 'pinned_at'
);
SET @ddl := IF(@has_pinned_at = 0,
    'ALTER TABLE `post_comment` ADD COLUMN `pinned_at` DATETIME DEFAULT NULL COMMENT ''置顶时间，NULL 表示未置顶'' AFTER `status`',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 置顶排序用到的索引（已存在时跳过，靠 information_schema 查索引名）
SET @has_pinned_index := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post_comment' AND INDEX_NAME = 'idx_comment_pinned'
);
SET @ddl := IF(@has_pinned_index = 0,
    'ALTER TABLE `post_comment` ADD KEY `idx_comment_pinned` (`post_id`, `pinned_at`)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 清理早期草案里的「帖子置顶」结构（v2.4 定稿改成评论置顶，表格已不再需要）
SET @has_post_pinned_index := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'treehole_post' AND INDEX_NAME = 'idx_pinned'
);
SET @ddl := IF(@has_post_pinned_index > 0,
    'ALTER TABLE `treehole_post` DROP KEY `idx_pinned`',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_post_pinned_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'treehole_post' AND COLUMN_NAME = 'pinned_at'
);
SET @ddl := IF(@has_post_pinned_col > 0,
    'ALTER TABLE `treehole_post` DROP COLUMN `pinned_at`',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) 会话表
CREATE TABLE IF NOT EXISTS `support_conversation` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`         BIGINT       NOT NULL COMMENT '发起咨询的用户',
    `assignee_id`     BIGINT       DEFAULT NULL COMMENT '接手的管理员/客服，NULL 表示待认领',
    `last_message`    VARCHAR(200) DEFAULT NULL COMMENT '最后一条消息摘要，列表页直接展示',
    `last_message_at` DATETIME     DEFAULT NULL COMMENT '最后一条消息时间，列表按它倒序',
    `user_unread`     INT          NOT NULL DEFAULT 0 COMMENT '用户未读的回复条数',
    `staff_unread`    INT          NOT NULL DEFAULT 0 COMMENT '客服/管理员未读的用户消息条数',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user` (`user_id`),
    KEY `idx_assignee` (`assignee_id`),
    KEY `idx_last_message_at` (`last_message_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='联系管理员/客服会话表';

-- 4) 会话消息表
CREATE TABLE IF NOT EXISTS `support_message` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT,
    `conversation_id` BIGINT        NOT NULL,
    `sender_id`       BIGINT        NOT NULL,
    `sender_role`     VARCHAR(20)   NOT NULL COMMENT '发送时定死的角色：USER/ADMIN/STAFF',
    `content`         VARCHAR(2000) NOT NULL,
    `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_conversation` (`conversation_id`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='联系管理员/客服消息表';
