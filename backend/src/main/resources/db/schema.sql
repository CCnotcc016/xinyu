-- 心语 (Xinyu) · AI 情绪日记与匿名树洞
-- Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
-- 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-- 心语 · 数据库初始化脚本 (MySQL 8)
-- 开发环境由 Spring Boot 自动执行；生产环境请手动导入后再启动（prod 已关闭自动建表）
-- 说明：脚本不写死库名（没有 USE 语句），表会建在连接指定的那个库里。
--       手动导入时请带上库名：mysql -uroot -p xinyu < backend/src/main/resources/db/schema.sql

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(50)  NOT NULL COMMENT '用户名/手机号',
    `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    `nickname`      VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `avatar`        VARCHAR(255) DEFAULT NULL COMMENT '头像 URL',
    `phone`         VARCHAR(20)  DEFAULT NULL COMMENT '手机号（注册必填；旧账号可后补，仅供危机干预联系）',
    `role`          VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER/ADMIN',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    -- 允许多行为 NULL（MySQL 唯一索引不约束 NULL），所以旧账号可以暂不绑定手机号
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

-- 情绪日记表
CREATE TABLE IF NOT EXISTS `diary` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT       NOT NULL COMMENT '作者',
    `content`       TEXT         NOT NULL COMMENT '日记正文',
    `voice_url`     VARCHAR(255) DEFAULT NULL COMMENT '语音地址（预留）',
    `emotion_score` INT          DEFAULT NULL COMMENT 'AI 情绪分 1-10',
    `emotion_label` VARCHAR(50)  DEFAULT NULL COMMENT 'AI 情绪标签',
    `weather`       VARCHAR(20)  DEFAULT NULL COMMENT '天气',
    `scene`         VARCHAR(50)  DEFAULT NULL COMMENT '场景',
    `privacy`       VARCHAR(20)  NOT NULL DEFAULT 'PRIVATE' COMMENT '隐私级别：PRIVATE/PUBLIC',
    `ai_reply`      TEXT         DEFAULT NULL COMMENT 'AI 共情回复',
    `ai_status`     VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'AI 状态：PENDING/DONE/FAILED/CRISIS',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='情绪日记表';

-- 标签表（情绪/天气/场景）
CREATE TABLE IF NOT EXISTS `tag` (
    `id`   BIGINT      NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(50) NOT NULL,
    `type` VARCHAR(20) NOT NULL COMMENT 'EMOTION/WEATHER/SCENE',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name_type` (`name`, `type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='标签表';

-- 日记-标签关联表
CREATE TABLE IF NOT EXISTS `diary_tag` (
    `id`       BIGINT NOT NULL AUTO_INCREMENT,
    `diary_id` BIGINT NOT NULL,
    `tag_id`   BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_diary` (`diary_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='日记标签关联表';

-- 匿名树洞帖子表
CREATE TABLE IF NOT EXISTS `treehole_post` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT        NOT NULL COMMENT '作者（匿名展示）',
    `content`       TEXT          NOT NULL,
    `images`        VARCHAR(1000) DEFAULT NULL COMMENT '图片 URL，逗号分隔',
    `like_count`    INT           NOT NULL DEFAULT 0,
    `comment_count` INT           NOT NULL DEFAULT 0,
    `status`        TINYINT       NOT NULL DEFAULT 1 COMMENT '1正常 0已下架',
    `is_anonymous`  TINYINT       NOT NULL DEFAULT 1 COMMENT '1匿名 0实名',
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_created` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='匿名树洞帖子表';

-- 帖子评论表
CREATE TABLE IF NOT EXISTS `post_comment` (
    `id`         BIGINT        NOT NULL AUTO_INCREMENT,
    `post_id`    BIGINT        NOT NULL,
    `user_id`    BIGINT        NOT NULL,
    `parent_id`  BIGINT        DEFAULT NULL COMMENT '被回复的评论 id，NULL 表示直接评论帖子',
    `content`    VARCHAR(1000) NOT NULL,
    `status`     TINYINT       NOT NULL DEFAULT 1 COMMENT '1正常 0已删除',
    `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_post` (`post_id`),
    KEY `idx_parent` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='帖子评论表';

-- 帖子点赞表
CREATE TABLE IF NOT EXISTS `post_like` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT,
    `post_id`    BIGINT   NOT NULL,
    `user_id`    BIGINT   NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_user` (`post_id`, `user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='帖子点赞表';

-- 举报表
CREATE TABLE IF NOT EXISTS `report` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `target_type` VARCHAR(20)  NOT NULL COMMENT 'POST/COMMENT/USER',
    `target_id`   BIGINT       NOT NULL,
    `reporter_id` BIGINT       NOT NULL,
    `reason`      VARCHAR(500) DEFAULT NULL,
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/HANDLED/DISMISSED',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='举报表';

-- 危机预警表
-- 树洞 / 日记中出现自残、自杀等危机表达时写入一条，供管理员跟进（后台「求助」页）
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

-- 每日情绪统计表
CREATE TABLE IF NOT EXISTS `emotion_daily_stat` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`        BIGINT        NOT NULL,
    `stat_date`      DATE          NOT NULL,
    `avg_score`      DECIMAL(4, 2) DEFAULT NULL,
    `diary_count`    INT           NOT NULL DEFAULT 0,
    `dominant_label` VARCHAR(50)   DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_date` (`user_id`, `stat_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='每日情绪统计表';
