-- 心语 v3.4 增量升级脚本（幂等，可重复执行）
--
-- 作用：离线补传的幂等键。
--   · diary.client_uid  App 在离线时先生成本地 id，联网后带着同一个 client_uid 重传；
--     客户端超时重试、用户手动点「立即同步」都可能把同一篇日记发两次，
--     接口按 (user_id, client_uid) 判重，重复请求直接返回已存在的那篇，不会写成两条。
--
-- 为什么不用 Redis 做幂等：和 v3.3 的登录锁定同理，本机演示环境常常没起 Redis，
-- 「重传不重复」属于数据正确性问题，落库最稳，也顺便能当排查线索。
--
-- 开发环境由 Spring Boot 启动时自动执行（见 application-dev.yml 的 schema-locations）；
-- 生产环境请手动执行一次：
--   mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v3.4.sql

-- 1) diary.client_uid
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'diary' AND COLUMN_NAME = 'client_uid') = 0,
    'ALTER TABLE `diary` ADD COLUMN `client_uid` VARCHAR(64) DEFAULT NULL COMMENT ''客户端幂等键，离线补传时防重复'' AFTER `ai_status`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 唯一索引：同一用户 + 同一个 client_uid 只允许一条
--    MySQL 唯一索引不约束 NULL，所以历史数据（client_uid 为 NULL）不会冲突
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'diary' AND INDEX_NAME = 'uk_user_client_uid') = 0,
    'ALTER TABLE `diary` ADD UNIQUE KEY `uk_user_client_uid` (`user_id`, `client_uid`)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
