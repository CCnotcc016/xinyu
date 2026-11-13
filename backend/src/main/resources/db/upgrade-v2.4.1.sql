-- 心语 v2.4.1 增量升级脚本（幂等，可重复执行）
--
-- 作用：给已存在的旧库补上 v2.4.1「客服结单」需要的结构。
--   · support_conversation.status            OPEN=进行中，CLOSED=客服已结单
--   · support_conversation.closed_at         结单时间
--   · support_conversation.closed_by_id      结单的客服/管理员
--   · support_conversation.session_start_id  本轮接待的起始消息 ID
--                                            （结单后用户再来问会开新一段会话，
--                                             客服/管理员只看得到 id 大于它的消息）
--
-- 与 upgrade-v2.sql ~ v2.4.sql 写法一致：先查 information_schema 再动态拼 DDL，
-- 因为 MySQL 8 不支持 ADD COLUMN IF NOT EXISTS（那是 MariaDB 的语法）。
--
-- 开发环境由 Spring Boot 启动时自动执行（见 application-dev.yml 的 schema-locations）；
-- 生产环境请手动执行一次：
--   mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v2.4.1.sql

-- 1) status
SET @has_status := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'support_conversation' AND COLUMN_NAME = 'status'
);
SET @ddl := IF(@has_status = 0,
    'ALTER TABLE `support_conversation` ADD COLUMN `status` VARCHAR(20) NOT NULL DEFAULT ''OPEN'' COMMENT ''OPEN=进行中，CLOSED=客服已结单'' AFTER `staff_unread`',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) closed_at
SET @has_closed_at := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'support_conversation' AND COLUMN_NAME = 'closed_at'
);
SET @ddl := IF(@has_closed_at = 0,
    'ALTER TABLE `support_conversation` ADD COLUMN `closed_at` DATETIME DEFAULT NULL COMMENT ''结单时间'' AFTER `status`',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) closed_by_id
SET @has_closed_by := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'support_conversation' AND COLUMN_NAME = 'closed_by_id'
);
SET @ddl := IF(@has_closed_by = 0,
    'ALTER TABLE `support_conversation` ADD COLUMN `closed_by_id` BIGINT DEFAULT NULL COMMENT ''结单的客服/管理员'' AFTER `closed_at`',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) session_start_id
SET @has_session_start := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'support_conversation' AND COLUMN_NAME = 'session_start_id'
);
SET @ddl := IF(@has_session_start = 0,
    'ALTER TABLE `support_conversation` ADD COLUMN `session_start_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''本轮接待的起始消息 ID'' AFTER `closed_by_id`',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5) 老库里的存量会话默认都是「进行中」，把起点对齐到 0（即所有历史消息都算本轮），
--    这样升级后客服还能看到升级前的聊天记录，不会突然变空。
UPDATE `support_conversation` SET `status` = 'OPEN' WHERE `status` IS NULL OR `status` = '';
