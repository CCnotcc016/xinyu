-- 心语 (Xinyu) · AI 情绪日记与匿名树洞
-- Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
-- 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-- 心语 v2.2 增量升级脚本（幂等，可重复执行）
--
-- 作用：给 v2.0 / v2.1 已经存在的旧库补上 v2.2 新增的结构。
--   · post_comment.parent_id 字段 + idx_parent 索引（树洞评论支持「回复某人」）
--
-- 写法与 upgrade-v2.sql 一致：先查 information_schema 再动态拼 DDL，
-- 因为 MySQL 8 不支持 ADD COLUMN IF NOT EXISTS（那是 MariaDB 的语法）。
--
-- 开发环境由 Spring Boot 启动时自动执行（见 application-dev.yml 的 schema-locations）；
-- 生产环境请手动执行一次：
--   mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v2.2.sql

-- 1) post_comment.parent_id
SET @has_parent_id := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post_comment' AND COLUMN_NAME = 'parent_id'
);
SET @ddl := IF(@has_parent_id = 0,
    'ALTER TABLE `post_comment` ADD COLUMN `parent_id` BIGINT DEFAULT NULL COMMENT ''被回复的评论 id，NULL 表示直接评论帖子'' AFTER `user_id`',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) post_comment.idx_parent
SET @has_idx_parent := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post_comment' AND INDEX_NAME = 'idx_parent'
);
SET @ddl := IF(@has_idx_parent = 0,
    'ALTER TABLE `post_comment` ADD KEY `idx_parent` (`parent_id`)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
