-- 心语 v3.6 增量升级脚本（幂等，可重复执行）
--
-- 作用：安全加固相关的表结构变更。
--   1) user.must_change_password  首次登录强制改密（管理员初始化账号用）
--   2) user.token_version         JWT 版本号，改密码 / 换手机号 / 退出登录时 +1，旧 token 立即失效
--   3) admin_audit_log            管理员敏感操作留痕（查看用户私密日记、删除内容）
--
-- 开发环境由 Spring Boot 启动时自动执行（见 application-dev.yml 的 schema-locations）；
-- 生产环境请手动执行一次：
--   mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v3.6.sql

-- 1) user.must_change_password
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'must_change_password') = 0,
    'ALTER TABLE `user` ADD COLUMN `must_change_password` TINYINT NOT NULL DEFAULT 0 COMMENT ''1 = 首次登录必须改密码'' AFTER `locked_until`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) user.token_version
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'token_version') = 0,
    'ALTER TABLE `user` ADD COLUMN `token_version` INT NOT NULL DEFAULT 0 COMMENT ''JWT 版本号，改动即让旧 token 失效'' AFTER `must_change_password`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 管理员敏感操作审计
CREATE TABLE IF NOT EXISTS `admin_audit_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `admin_id`    BIGINT       NOT NULL COMMENT '操作管理员',
    `action`      VARCHAR(40)  NOT NULL COMMENT 'REVEAL_DIARY / DELETE_DIARY / DELETE_POST / DELETE_USER',
    `target_type` VARCHAR(20)  NOT NULL COMMENT 'DIARY / POST / USER',
    `target_id`   BIGINT       NOT NULL COMMENT '目标对象 ID',
    `target_user_id` BIGINT    DEFAULT NULL COMMENT '当事用户（被查看 / 被删除内容的作者）',
    `reason`      VARCHAR(200) DEFAULT NULL COMMENT '管理员填写的理由（私密内容必须填）',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_admin_time` (`admin_id`, `created_at`),
    KEY `idx_target` (`target_type`, `target_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='管理员敏感操作审计日志';

-- 4) 老库里 admin_audit_log 已经存在但没有 target_user_id（CREATE TABLE IF NOT EXISTS 不会补字段）
SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_audit_log' AND COLUMN_NAME = 'target_user_id') = 0,
    'ALTER TABLE `admin_audit_log` ADD COLUMN `target_user_id` BIGINT DEFAULT NULL COMMENT ''当事用户（被查看 / 被删除内容的作者）'' AFTER `target_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5) 老预警里的摘录最长存了 500 字（几乎等于日记全文），统一收敛成短摘录。
--    新写入的预警在 CrisisAlertService 里已经会按关键词居中截断，这里只处理历史数据。
UPDATE `crisis_alert`
SET `content` = CONCAT(LEFT(`content`, 60), '…')
WHERE `content` IS NOT NULL AND CHAR_LENGTH(`content`) > 60;
