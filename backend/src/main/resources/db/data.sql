-- 初始化数据：默认管理员 + 常用标签
USE `xinyu`;

-- 默认管理员：admin / zNc40171316（BCrypt 哈希，如需更换请重新生成）
-- 注意：该语句仅在 admin 不存在时执行，修改此处不会影响已存在的账号
INSERT INTO `user` (`username`, `password_hash`, `nickname`, `role`, `status`)
SELECT 'admin', '$2a$10$VaUmLpwbPO8WUX7NPbUy9urehsdCn/OQ7K.A5xX9h06E0VpuetvZm', '系统管理员', 'ADMIN', 1
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'admin');

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
