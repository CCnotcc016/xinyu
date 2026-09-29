-- 心语 (Xinyu) · AI 情绪日记与匿名树洞
-- Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
-- 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-- 初始化数据：常用标签（不包含任何账号）
-- 说明：脚本不写死库名（没有 USE 语句），数据会插到连接指定的那个库里。

-- 说明：本仓库不预置任何账号，也没有任何默认密码。
-- 请部署后用 `bash scripts/init-admin.sh` 创建你自己的管理员（会写入 BCrypt 哈希）。

-- 情绪标签
INSERT INTO `tag` (`name`, `type`) VALUES
('开心', 'EMOTION'), ('平静', 'EMOTION'), ('焦虑', 'EMOTION'),
('低落', 'EMOTION'), ('愤怒', 'EMOTION'), ('疲惫', 'EMOTION'), ('孤独', 'EMOTION')
ON DUPLICATE KEY UPDATE `name` = `name`;

-- 天气标签
INSERT INTO `tag` (`name`, `type`) VALUES
('晴天', 'WEATHER'), ('阴天', 'WEATHER'), ('雨天', 'WEATHER'), ('雪天', 'WEATHER')
ON DUPLICATE KEY UPDATE `name` = `name`;

-- 场景标签
INSERT INTO `tag` (`name`, `type`) VALUES
('学习', 'SCENE'), ('工作', 'SCENE'), ('家庭', 'SCENE'), ('感情', 'SCENE'), ('社交', 'SCENE')
ON DUPLICATE KEY UPDATE `name` = `name`;
