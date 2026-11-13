-- 心语 · 数据库初始化脚本 (MySQL 8)
-- 开发环境由 Spring Boot 自动执行；生产环境请手动导入后再启动（prod 已关闭自动建表）

CREATE DATABASE IF NOT EXISTS `xinyu` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `xinyu`;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(50)  NOT NULL COMMENT '用户名/手机号',
    `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    `nickname`      VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `avatar`        VARCHAR(255) DEFAULT NULL COMMENT '头像 URL',
    `phone`         VARCHAR(20)  DEFAULT NULL COMMENT '手机号（注册必填；旧账号可后补，仅供危机干预联系）',
    `agreement_version`     VARCHAR(20) DEFAULT NULL COMMENT '已同意的协议版本，NULL 表示从未同意过',
    `agreement_accepted_at` DATETIME    DEFAULT NULL COMMENT '同意时间',
    `location_consent`      TINYINT     NOT NULL DEFAULT 0 COMMENT '是否授权定位换天气：1 是 0 否',
    `location_consent_at`   DATETIME    DEFAULT NULL COMMENT '最近一次授权 / 撤回定位的时间',
    `role`          VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER/ADMIN/STAFF（STAFF = 客服）',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
    `failed_attempts` INT        NOT NULL DEFAULT 0 COMMENT '连续登录失败次数，登录成功清零',
    `locked_until`    DATETIME   DEFAULT NULL COMMENT '登录锁定到期时间，NULL 表示未锁定',
    `must_change_password` TINYINT NOT NULL DEFAULT 0 COMMENT '1 = 首次登录必须改密码（管理员初始化账号）',
    `token_version`   INT        NOT NULL DEFAULT 0 COMMENT 'JWT 版本号：改密码 / 换手机号 / 退出登录时 +1，旧 token 立即失效',
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
    `client_uid`    VARCHAR(64)  DEFAULT NULL COMMENT '客户端幂等键，离线补传时防重复',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at`    DATETIME     DEFAULT NULL COMMENT '进回收站的时间，NULL 表示未删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_user_deleted` (`user_id`, `deleted_at`),
    -- 离线补传的幂等键：重复提交同一篇日记只会落一条
    UNIQUE KEY `uk_user_client_uid` (`user_id`, `client_uid`)
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
    `pinned_at`  DATETIME      DEFAULT NULL COMMENT '置顶时间，NULL 表示未置顶；贴主每个帖子最多置顶一条',
    `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_post` (`post_id`),
    KEY `idx_parent` (`parent_id`),
    KEY `idx_comment_pinned` (`post_id`, `pinned_at`)
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
    `remark`      VARCHAR(255) DEFAULT NULL COMMENT '处理说明，举报人可见',
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
    `follow_up_at`   DATETIME     DEFAULT NULL COMMENT '最近一次回访时间',
    `follow_up_note` VARCHAR(255) DEFAULT NULL COMMENT '回访情况：是否联系上、状态如何',
    `follow_up_by`   BIGINT       DEFAULT NULL COMMENT '回访人',
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

-- 「联系管理员/客服」会话表
-- 一个用户只有一条会话（用户看到的是「我和客服」这一条线），
-- assignee_id 记录谁接手，客服账号只能看自己接手的会话。
CREATE TABLE IF NOT EXISTS `support_conversation` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`         BIGINT       NOT NULL COMMENT '发起咨询的用户',
    `assignee_id`     BIGINT       DEFAULT NULL COMMENT '接手的管理员/客服，NULL 表示待认领',
    `last_message`    VARCHAR(200) DEFAULT NULL COMMENT '最后一条消息摘要，列表页直接展示',
    `last_message_at` DATETIME     DEFAULT NULL COMMENT '最后一条消息时间，列表按它倒序',
    `user_unread`     INT          NOT NULL DEFAULT 0 COMMENT '用户未读的回复条数',
    `staff_unread`    INT          NOT NULL DEFAULT 0 COMMENT '客服/管理员未读的用户消息条数',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN=进行中，CLOSED=客服已结单',
    `closed_at`       DATETIME     DEFAULT NULL COMMENT '结单时间',
    `closed_by_id`    BIGINT       DEFAULT NULL COMMENT '结单的客服/管理员',
    -- 结单后用户再来问会开一段新会话，客服/管理员只看得到 id 大于它的消息（之前的记录不再展示）
    `session_start_id` BIGINT      NOT NULL DEFAULT 0 COMMENT '本轮接待的起始消息 ID',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    -- 一个用户一条会话，靠唯一索引兜住并发下的重复创建
    UNIQUE KEY `uk_user` (`user_id`),
    KEY `idx_assignee` (`assignee_id`),
    KEY `idx_last_message_at` (`last_message_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='联系管理员/客服会话表';

-- 客服会话消息表
CREATE TABLE IF NOT EXISTS `support_message` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT,
    `conversation_id` BIGINT        NOT NULL,
    `sender_id`       BIGINT        NOT NULL,
    `sender_role`     VARCHAR(20)   NOT NULL COMMENT '发送时定死的角色：USER/ADMIN/STAFF/SYSTEM',
    `content`         VARCHAR(2000) NOT NULL,
    `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_conversation` (`conversation_id`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='联系管理员/客服消息表';

-- —— v3.0.0 起新增：好友 + 私聊 + AI 情绪周报 ——
-- （老库由 upgrade-v3.0.sql 补齐，这里保证新建的库一步到位）

-- 好友关系表：A 和 B 成为好友时插入 (A,B) 和 (B,A) 两行
CREATE TABLE IF NOT EXISTS `friend` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT   NOT NULL COMMENT '拥有这行的人',
    `friend_id`  BIGINT   NOT NULL COMMENT '好友',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '成为好友的时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_friend` (`user_id`, `friend_id`),
    KEY `idx_friend` (`friend_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='好友关系表（双向各一行）';

-- 好友申请表
CREATE TABLE IF NOT EXISTS `friend_request` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `from_user_id` BIGINT       NOT NULL COMMENT '申请人',
    `to_user_id`   BIGINT       NOT NULL COMMENT '被申请人',
    `message`      VARCHAR(100) DEFAULT NULL COMMENT '申请附言',
    `status`       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED',
    `handled_at`   DATETIME     DEFAULT NULL COMMENT '同意或拒绝的时间',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_to_status` (`to_user_id`, `status`, `created_at`),
    KEY `idx_from_status` (`from_user_id`, `status`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='好友申请表';

-- 一对一私聊消息表
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT,
    `from_user_id` BIGINT        NOT NULL COMMENT '发送者',
    `to_user_id`   BIGINT        NOT NULL COMMENT '接收者',
    `content`      VARCHAR(1000) NOT NULL COMMENT '文本内容',
    `read_at`      DATETIME      DEFAULT NULL COMMENT '对方读这条消息的时间，NULL=未读',
    `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_from` (`from_user_id`, `id`),
    KEY `idx_to_unread` (`to_user_id`, `read_at`),
    KEY `idx_pair` (`from_user_id`, `to_user_id`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='私聊消息表';

-- AI 情绪周报缓存表
CREATE TABLE IF NOT EXISTS `emotion_weekly_report` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`        BIGINT        NOT NULL,
    `week_start`     DATE          NOT NULL COMMENT '该周周一',
    `diary_count`    INT           NOT NULL DEFAULT 0,
    `avg_score`      DECIMAL(4, 2) DEFAULT NULL COMMENT '本周平均情绪分',
    `dominant_label` VARCHAR(20)   DEFAULT NULL COMMENT '出现最多的情绪标签',
    `keywords`       VARCHAR(255)  DEFAULT NULL COMMENT '本周高频标签，逗号分隔',
    `content`        VARCHAR(2000) DEFAULT NULL COMMENT 'AI 生成的周报正文',
    `source`         VARCHAR(20)   NOT NULL DEFAULT 'AI' COMMENT 'AI=真实大模型，MOCK=离线兜底',
    `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_week` (`user_id`, `week_start`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='AI 情绪周报表';

-- 站内通知表（v3.1）：点赞 / 评论 / 好友 / 私聊 / 客服回复 / 举报回执
CREATE TABLE IF NOT EXISTS `notification` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL COMMENT '接收通知的人',
    `type`        VARCHAR(20)  NOT NULL COMMENT 'LIKE/COMMENT/COMMENT_REPLY/FRIEND_REQUEST/FRIEND_ACCEPT/CHAT/SUPPORT/REPORT',
    `title`       VARCHAR(100) NOT NULL COMMENT '列表上的一行标题',
    `content`     VARCHAR(255) DEFAULT NULL COMMENT '摘要，太长会截断',
    `target_type` VARCHAR(20)  DEFAULT NULL COMMENT 'POST/COMMENT/USER/CONVERSATION',
    `target_id`   BIGINT       DEFAULT NULL COMMENT '点击后要跳到的对象 ID',
    `is_read`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未读 / 1 已读',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_read` (`user_id`, `is_read`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='站内通知表';

-- 敏感词库（v3.2）：后台可维护，启动时若为空自动从 sensitive-words.txt 导入
CREATE TABLE IF NOT EXISTS `sensitive_word` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `word`       VARCHAR(50) NOT NULL COMMENT '敏感词，按包含匹配',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_word` (`word`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='敏感词库';

-- 管理员敏感操作审计（v3.6）：查看用户私密日记、删除内容等必须留痕
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
