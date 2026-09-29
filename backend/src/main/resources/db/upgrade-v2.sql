-- 心语 (Xinyu) · AI 情绪日记与匿名树洞
-- Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
-- 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-- 心语 v2.0 增量升级脚本（幂等，可重复执行）
--
-- 作用：给 v1.x 已经存在的旧库补上 v2.0 新增的结构。
--   · user.phone 字段 + uk_phone 唯一索引（注册必填手机号 / 危机干预联系）
--   · crisis_alert 表在 schema.sql 里用 CREATE TABLE IF NOT EXISTS 创建，这里不用管
--
-- 为什么不用 ALTER TABLE ... IF NOT EXISTS：
--   MySQL 8 不支持列的 IF NOT EXISTS（那是 MariaDB 的语法），
--   所以这里改成「查 information_schema → 动态拼 SQL」的写法，保证重复执行不报错。
--
-- 开发环境由 Spring Boot 启动时自动执行（见 application-dev.yml 的 schema-locations）；
-- 生产环境请手动执行一次：
--   mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v2.sql

-- 1) user.phone
SET @has_phone := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'phone'
);
SET @ddl := IF(@has_phone = 0,
    'ALTER TABLE `user` ADD COLUMN `phone` VARCHAR(20) DEFAULT NULL AFTER `avatar`',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) user.uk_phone
SET @has_uk_phone := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'uk_phone'
);
SET @ddl := IF(@has_uk_phone = 0,
    'ALTER TABLE `user` ADD UNIQUE KEY `uk_phone` (`phone`)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) crisis_alert 表（与 schema.sql 保持一致，供手动升级时也能一次建好）
CREATE TABLE IF NOT EXISTS `crisis_alert` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL COMMENT '触发用户',
    `target_type` VARCHAR(20)  NOT NULL COMMENT 'POST/DIARY',
    `target_id`   BIGINT       DEFAULT NULL COMMENT '内容 ID（内容已删除时仍保留记录）',
    `keyword`     VARCHAR(50)  DEFAULT NULL COMMENT '命中的危机关键词',
    `content`     VARCHAR(500) DEFAULT NULL COMMENT '内容摘要',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/HANDLED/DISMISSED',
    `remark`      VARCHAR(255) DEFAULT NULL COMMENT '管理员备注',
    `handled_by`  BIGINT       DEFAULT NULL COMMENT '处理人',
    `handled_at`  DATETIME     DEFAULT NULL COMMENT '处理时间',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_status_created` (`status`, `created_at`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='危机预警表';
