-- 初始化数据：常用标签
USE `xinyu`;

-- 这里刻意不再写死管理员账号。
-- 旧版本在 SQL 里 INSERT 了一个密码公开的管理员，只要部署到公网就等于把后台交出去。
-- 现在管理员由 AdminBootstrap 在启动时按环境变量创建（ADMIN_INIT_USERNAME / ADMIN_INIT_PASSWORD）：
--   · 密码必须满足 PasswordPolicy（8-32 位、字母+数字、非弱口令）；
--   · 只在库里一个可用管理员都没有时才创建，不会覆盖已有账号；
--   · 创建的账号带 must_change_password=1，首次登录必须先改密码。

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
