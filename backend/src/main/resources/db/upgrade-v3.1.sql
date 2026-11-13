-- 心语 v3.1 增量升级脚本（幂等，可重复执行）
--
-- 作用：补上 v3.1 新增的结构。
--   · treehole_post.images  树洞配图（逗号分隔的图片 URL）
--   · notification          站内通知（点赞 / 评论 / 好友 / 私聊 / 客服回复）
--
-- 沿用 v2.0 起的写法：MySQL 8 不支持列的 IF NOT EXISTS，
-- 所以查 information_schema → 动态拼 SQL，重复执行不会报错。
--
-- 开发环境由 Spring Boot 启动时自动执行（见 application-dev.yml 的 schema-locations）；
-- 生产环境请手动执行一次：
--   mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v3.1.sql

-- 1) treehole_post.images
SET @has_images := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'treehole_post' AND COLUMN_NAME = 'images'
);
SET @ddl := IF(@has_images = 0,
    'ALTER TABLE `treehole_post` ADD COLUMN `images` VARCHAR(1000) DEFAULT NULL COMMENT ''图片 URL，逗号分隔'' AFTER `content`',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) notification
CREATE TABLE IF NOT EXISTS `notification` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL COMMENT '接收通知的人',
    `type`        VARCHAR(20)  NOT NULL COMMENT 'LIKE/COMMENT/COMMENT_REPLY/FRIEND_REQUEST/FRIEND_ACCEPT/CHAT/SUPPORT',
    `title`       VARCHAR(100) NOT NULL COMMENT '列表上的一行标题',
    `content`     VARCHAR(255) DEFAULT NULL COMMENT '摘要，太长会截断',
    `target_type` VARCHAR(20)  DEFAULT NULL COMMENT 'POST/COMMENT/USER/CONVERSATION',
    `target_id`   BIGINT       DEFAULT NULL COMMENT '点击后要跳到的对象 ID',
    `is_read`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未读 / 1 已读',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_read` (`user_id`, `is_read`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='站内通知表';
