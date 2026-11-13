-- 心语 v2.3 增量升级脚本（幂等，可重复执行）
--
-- 作用：给已存在的旧库补上「用户同意」相关字段，支撑 v2.3 的用户协议 / 隐私政策 / 定位授权。
--   · user.agreement_version      已同意的协议版本（后端与 Legal.CURRENT_VERSION 比对）
--   · user.agreement_accepted_at  同意时间，作为「用户确实同意过」的凭证
--   · user.location_consent       是否授权用定位换天气：1 同意 0 未同意或已撤回
--   · user.location_consent_at    最近一次授权 / 撤回的时间
--
-- 注意：存量用户这四个字段都是 NULL / 0，登录后会被前端强制弹「政策更新」弹窗要求重新确认。
-- 这是刻意的——不能替用户默认同意，只能等他本人点过「我已阅读并同意」。
--
-- 写法与 upgrade-v2.sql / upgrade-v2.2.sql 一致：先查 information_schema 再动态拼 DDL，
-- 因为 MySQL 8 不支持 ADD COLUMN IF NOT EXISTS（那是 MariaDB 的语法）。
--
-- 开发环境由 Spring Boot 启动时自动执行（见 application-dev.yml 的 schema-locations）；
-- 生产环境请手动执行一次：
--   mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v2.3.sql

-- 1) user.agreement_version
SET @has_agreement_version := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'agreement_version'
);
SET @ddl := IF(@has_agreement_version = 0,
    'ALTER TABLE `user` ADD COLUMN `agreement_version` VARCHAR(20) DEFAULT NULL COMMENT ''已同意的协议版本，NULL 表示从未同意过'' AFTER `phone`',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) user.agreement_accepted_at
SET @has_agreement_accepted_at := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'agreement_accepted_at'
);
SET @ddl := IF(@has_agreement_accepted_at = 0,
    'ALTER TABLE `user` ADD COLUMN `agreement_accepted_at` DATETIME DEFAULT NULL COMMENT ''同意时间'' AFTER `agreement_version`',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) user.location_consent
SET @has_location_consent := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'location_consent'
);
SET @ddl := IF(@has_location_consent = 0,
    'ALTER TABLE `user` ADD COLUMN `location_consent` TINYINT NOT NULL DEFAULT 0 COMMENT ''是否授权定位换天气：1 是 0 否'' AFTER `agreement_accepted_at`',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) user.location_consent_at
SET @has_location_consent_at := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'location_consent_at'
);
SET @ddl := IF(@has_location_consent_at = 0,
    'ALTER TABLE `user` ADD COLUMN `location_consent_at` DATETIME DEFAULT NULL COMMENT ''最近一次授权 / 撤回定位的时间'' AFTER `location_consent`',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
