-- 心语 v3.0.0 增量升级脚本（幂等，可重复执行）
--
-- 作用：新增 v3.0.0 的「好友 + 私聊」与「AI 情绪周报」需要的新表。
--   · friend                 好友关系（双向各存一行，查询时不用再判断方向）
--   · friend_request         好友申请（PENDING/ACCEPTED/REJECTED）
--   · chat_message           一对一私聊消息（read_at 为空即未读）
--   · emotion_weekly_report  AI 周报缓存（同一用户同一周只生成一次）
--
-- 全部用 CREATE TABLE IF NOT EXISTS，重复执行不会报错，也不会动任何已有数据；
-- 老用户升级后会多出这几个空表，不影响 v2.x 的任何功能。
--
-- 开发环境由 Spring Boot 启动时自动执行（见 application-dev.yml 的 schema-locations）；
-- 生产环境请手动执行一次：
--   mysql -uroot -p xinyu < backend/src/main/resources/db/upgrade-v3.0.sql

-- 1) 好友关系：A 和 B 成为好友时插入 (A,B) 和 (B,A) 两行，查「我的好友」只需 WHERE user_id = me
CREATE TABLE IF NOT EXISTS `friend` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT   NOT NULL COMMENT '拥有这行的人',
    `friend_id`  BIGINT   NOT NULL COMMENT '好友',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '成为好友的时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_friend` (`user_id`, `friend_id`),
    KEY `idx_friend` (`friend_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='好友关系表（双向各一行）';

-- 2) 好友申请
--    不加 (from,to,status) 唯一索引：被拒后要允许再次申请，靠业务层保证同时只有一条 PENDING
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

-- 3) 一对一私聊消息
--    没有做「会话表」：两人的会话直接用 (from_user_id, to_user_id) 配对还原，
--    会话列表取每对好友的最新一条消息（见 ChatMapper.selectConversations）。
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

-- 4) AI 情绪周报缓存（同一个人同一周只调用一次大模型，省钱也省等待）
CREATE TABLE IF NOT EXISTS `emotion_weekly_report` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`         BIGINT        NOT NULL,
    `week_start`      DATE          NOT NULL COMMENT '该周周一',
    `diary_count`     INT           NOT NULL DEFAULT 0,
    `avg_score`       DECIMAL(4, 2) DEFAULT NULL COMMENT '本周平均情绪分',
    `dominant_label`  VARCHAR(20)   DEFAULT NULL COMMENT '出现最多的情绪标签',
    `keywords`        VARCHAR(255)  DEFAULT NULL COMMENT '本周高频标签，逗号分隔',
    `content`         VARCHAR(2000) DEFAULT NULL COMMENT 'AI 生成的周报正文',
    `source`          VARCHAR(20)   NOT NULL DEFAULT 'AI' COMMENT 'AI=真实大模型，MOCK=离线兜底',
    `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_week` (`user_id`, `week_start`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='AI 情绪周报表';
