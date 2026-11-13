-- 心语 v3.3 增量升级脚本（幂等，可重复执行）
--
-- 作用：登录失败锁定所需的两个字段。
--   · user.failed_attempts 连续登录失败次数（登录成功即清零）
--   · user.locked_until    锁定到期时间，NULL 表示未锁定
--
-- 为什么放在库里而不是 Redis：本机演示环境常常没起 Redis，
-- 限流服务在 Redis 不可用时会放行，等于把爆破防护关掉；
-- 账号锁定属于「不能失效」的保护，落库最稳。
--
-- 开发环境由 Spring Boot 启动时自动执行（见 application-dev.yml 的 schema-locations）；
-- 生产环境请手动执行一次：
--   mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v3.3.sql

SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'failed_attempts') = 0,
    'ALTER TABLE `user`
        ADD COLUMN `failed_attempts` INT NOT NULL DEFAULT 0 COMMENT ''连续登录失败次数，登录成功清零'' AFTER `status`,
        ADD COLUMN `locked_until`    DATETIME DEFAULT NULL COMMENT ''登录锁定到期时间，NULL 表示未锁定'' AFTER `failed_attempts`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
