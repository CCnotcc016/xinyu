-- 心语 · 演示数据（幂等，可重复执行）
--
-- 作用：给本地 / 答辩演示准备一批「看起来像真实使用过」的数据。
--   · 5 个普通用户，各带 3 篇日记（含情绪分、情绪标签、AI 共情回复）与树洞帖子
--   · 2 个客服账号（role = STAFF），用来演示「联系管理员/客服」
--   · 1 条被贴主置顶的评论区留言、1 条评论与点赞数，方便一眼看到这两处功能
--
-- 账号密码与默认管理员一致（见 data.sql 的注释与 README「演示账号」一节）。
-- 只挂在 dev 的 data-locations 上，生产环境不会执行；不需要时把 application-dev.yml
-- 里的 seed-demo.sql 去掉即可，这些数据也可以直接在后台管理里删掉。
--
-- 写法说明：每条 INSERT 都带 NOT EXISTS 守卫，重复执行不会产生重复数据。

USE `xinyu`;

-- 1) 5 个普通用户 + 2 个客服（客服角色 STAFF，只能看自己接手的会话）
INSERT INTO `user` (`username`, `password_hash`, `nickname`, `phone`, `agreement_version`, `agreement_accepted_at`, `location_consent`, `role`, `status`)
SELECT 'demo01', '$2a$10$VaUmLpwbPO8WUX7NPbUy9urehsdCn/OQ7K.A5xX9h06E0VpuetvZm', '小满', '13900000001', '1.0', NOW(), 0, 'USER', 1
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'demo01');

INSERT INTO `user` (`username`, `password_hash`, `nickname`, `phone`, `agreement_version`, `agreement_accepted_at`, `location_consent`, `role`, `status`)
SELECT 'demo02', '$2a$10$VaUmLpwbPO8WUX7NPbUy9urehsdCn/OQ7K.A5xX9h06E0VpuetvZm', '阿宁', '13900000002', '1.0', NOW(), 0, 'USER', 1
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'demo02');

INSERT INTO `user` (`username`, `password_hash`, `nickname`, `phone`, `agreement_version`, `agreement_accepted_at`, `location_consent`, `role`, `status`)
SELECT 'demo03', '$2a$10$VaUmLpwbPO8WUX7NPbUy9urehsdCn/OQ7K.A5xX9h06E0VpuetvZm', '竹间', '13900000003', '1.0', NOW(), 0, 'USER', 1
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'demo03');

INSERT INTO `user` (`username`, `password_hash`, `nickname`, `phone`, `agreement_version`, `agreement_accepted_at`, `location_consent`, `role`, `status`)
SELECT 'demo04', '$2a$10$VaUmLpwbPO8WUX7NPbUy9urehsdCn/OQ7K.A5xX9h06E0VpuetvZm', '十一', '13900000004', '1.0', NOW(), 0, 'USER', 1
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'demo04');

INSERT INTO `user` (`username`, `password_hash`, `nickname`, `phone`, `agreement_version`, `agreement_accepted_at`, `location_consent`, `role`, `status`)
SELECT 'demo05', '$2a$10$VaUmLpwbPO8WUX7NPbUy9urehsdCn/OQ7K.A5xX9h06E0VpuetvZm', '北岛', '13900000005', '1.0', NOW(), 0, 'USER', 1
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'demo05');

INSERT INTO `user` (`username`, `password_hash`, `nickname`, `phone`, `agreement_version`, `agreement_accepted_at`, `location_consent`, `role`, `status`)
SELECT 'service01', '$2a$10$VaUmLpwbPO8WUX7NPbUy9urehsdCn/OQ7K.A5xX9h06E0VpuetvZm', '小语', '13900000006', '1.0', NOW(), 0, 'STAFF', 1
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'service01');

INSERT INTO `user` (`username`, `password_hash`, `nickname`, `phone`, `agreement_version`, `agreement_accepted_at`, `location_consent`, `role`, `status`)
SELECT 'service02', '$2a$10$VaUmLpwbPO8WUX7NPbUy9urehsdCn/OQ7K.A5xX9h06E0VpuetvZm', '小舟', '13900000007', '1.0', NOW(), 0, 'STAFF', 1
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'service02');

-- 2) 日记（每人的插入条件都是「自己还没有日记」，所以整段只会生效一次）
SET @u01 := (SELECT `id` FROM `user` WHERE `username` = 'demo01');
INSERT INTO `diary` (`user_id`, `content`, `emotion_score`, `emotion_label`, `weather`, `scene`, `privacy`, `ai_reply`, `ai_status`, `created_at`)
SELECT @u01, x.content, x.score, x.label, x.weather, x.scene, 'PRIVATE', x.reply, 'DONE',
       DATE_SUB(NOW(), INTERVAL x.hours HOUR)
FROM (
    SELECT '今天终于把拖了两周的初稿写完了。虽然还很粗糙，但至少动起来了。' AS content, 7 AS score,
           '开心' AS label, '晴天' AS weather, '学习' AS scene,
           '把「一直放着」变成「已经在这里」，这一步比想象中更不容易。要不要留十分钟喝口水，然后只挑一小段读一遍？' AS reply,
           5 AS hours
    UNION ALL SELECT '和朋友吃饭聊得挺开心，回到家之后反而有点空。', 5, '孤独', '阴天', '社交',
           '热闹之后的安静常常让落空感更明显，这不是你矫情。可以把那顿饭里最喜欢的一个瞬间写下来，把温度留久一点。', 30
    UNION ALL SELECT '加班到十点半，回家只想躺着，什么都没做却更累了。', 4, '疲惫', '雨天', '工作',
           '这种「什么都没做也很累」多半不是懒，而是消耗太久了。今晚可以把睡觉排在第一位，其他事明天再说。', 52
) x
WHERE @u01 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `diary` d WHERE d.`user_id` = @u01);

SET @u02 := (SELECT `id` FROM `user` WHERE `username` = 'demo02');
INSERT INTO `diary` (`user_id`, `content`, `emotion_score`, `emotion_label`, `weather`, `scene`, `privacy`, `ai_reply`, `ai_status`, `created_at`)
SELECT @u02, x.content, x.score, x.label, x.weather, x.scene, 'PRIVATE', x.reply, 'DONE',
       DATE_SUB(NOW(), INTERVAL x.hours HOUR)
FROM (
    SELECT '面试挂了，回来的路上一直在想，是不是自己哪里都不够好。' AS content, 3 AS score,
           '低落' AS label, '阴天' AS weather, '工作' AS scene,
           '一次面试的结果只说明匹配度，说明不了你的价值。可以把被问住的那个问题记下来，它是一条提示，不是判决。' AS reply,
           26 AS hours
    UNION ALL SELECT '晚上去江边走了四十分钟，风很舒服，脑子终于安静了一点。', 7, '平静', '晴天', NULL,
           '散步时思路变轻是常有的事，身体动起来，情绪就有地方去。可以把这条记成「对我有用的方法」，下次再借它一次。', 74
    UNION ALL SELECT '妈妈打电话问我最近怎么样，我说挺好的，其实不太好。', 4, '孤独', '阴天', '家庭',
           '你选了「挺好的」，大概是不想让她担心，这份体贴很温柔，但也把一些重量留给了自己。也许可以挑一件小事告诉她，不必说全部。', 120
) x
WHERE @u02 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `diary` d WHERE d.`user_id` = @u02);

SET @u03 := (SELECT `id` FROM `user` WHERE `username` = 'demo03');
INSERT INTO `diary` (`user_id`, `content`, `emotion_score`, `emotion_label`, `weather`, `scene`, `privacy`, `ai_reply`, `ai_status`, `created_at`)
SELECT @u03, x.content, x.score, x.label, x.weather, x.scene, 'PRIVATE', x.reply, 'DONE',
       DATE_SUB(NOW(), INTERVAL x.hours HOUR)
FROM (
    SELECT '终于把拖了很久的体检预约好了，有点紧张，也有点安心。' AS content, 6 AS score,
           '平静' AS label, '晴天' AS weather, '家庭' AS scene,
           '把身体的事排上日程，本身就是一种照顾自己。紧张是对结果的在意，不必赶走它。' AS reply,
           9 AS hours
    UNION ALL SELECT '和室友因为打扫的事吵了一架，事后觉得自己声音太大了。', 4, '愤怒', '阴天', '家庭',
           '生气常常是因为边界被碰到了，那说明你在意这段共同生活的秩序。等情绪落一点，只说事实和感受，不用先道歉也不用先指责。', 44
    UNION ALL SELECT '重新开始跑步，走跑结合三公里，比想象中难。', 7, '开心', '晴天', NULL,
           '第一天就把三公里跑完，已经超出很多人的第一步。明天哪怕只走一公里，也算数。', 140
) x
WHERE @u03 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `diary` d WHERE d.`user_id` = @u03);

SET @u04 := (SELECT `id` FROM `user` WHERE `username` = 'demo04');
INSERT INTO `diary` (`user_id`, `content`, `emotion_score`, `emotion_label`, `weather`, `scene`, `privacy`, `ai_reply`, `ai_status`, `created_at`)
SELECT @u04, x.content, x.score, x.label, x.weather, x.scene, 'PRIVATE', x.reply, 'DONE',
       DATE_SUB(NOW(), INTERVAL x.hours HOUR)
FROM (
    SELECT '把房间彻底收拾了一遍，扔掉三袋没用的东西，心情也跟着轻了一点。' AS content, 8 AS score,
           '开心' AS label, '晴天' AS weather, NULL AS scene,
           '整理外部空间常常会顺带整理到里面。今天这份轻可以记下来，下次心里堵的时候也去收一个抽屉。' AS reply, 3 AS hours
    UNION ALL SELECT '晚上吃了很辣的外卖，辣到流眼泪，居然挺爽。', 6, '平静', '雨天', '社交',
           '有些眼泪不需要理由，辣一辣也算一种释放。今晚早点睡。', 33
    UNION ALL SELECT '想到下个月房租和账单，突然有点喘不过气。', 3, '焦虑', '阴天', '工作',
           '把「喘不过气」拆成能动手的事会轻一些：先列一列这个月必须花的钱，剩下的部分再想办法。你不必一次解决全部。', 68
) x
WHERE @u04 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `diary` d WHERE d.`user_id` = @u04);

SET @u05 := (SELECT `id` FROM `user` WHERE `username` = 'demo05');
INSERT INTO `diary` (`user_id`, `content`, `emotion_score`, `emotion_label`, `weather`, `scene`, `privacy`, `ai_reply`, `ai_status`, `created_at`)
SELECT @u05, x.content, x.score, x.label, x.weather, x.scene, 'PRIVATE', x.reply, 'DONE',
       DATE_SUB(NOW(), INTERVAL x.hours HOUR)
FROM (
    SELECT '今天给自己煮了面，加了两个蛋，吃完觉得今天也不算太差。' AS content, 7 AS score,
           '开心' AS label, '晴天' AS weather, NULL AS scene,
           '给自己认真煮一顿饭，是很实在的照顾。这种「不算太差」的日子，值得多记几笔。' AS reply,
           6 AS hours
    UNION ALL SELECT '有时候很想找个人说话，打开对话框又不知道从哪句开始。', 4, '孤独', '阴天', '社交',
           '不知道从哪句开始，往往是因为想说的太多。可以从一句「我最近有点累」开始，剩下的会慢慢流出来。', 50
    UNION ALL SELECT '被领导当众说了一顿，回家路上一直在回放那个画面。', 3, '低落', '雨天', '工作',
           '被当众指出问题，难受的不只是内容，还有被看见的那一瞬。回放是大脑在找安全感，试着给自己设个时限：想十分钟，然后去洗个热水澡。', 96
) x
WHERE @u05 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `diary` d WHERE d.`user_id` = @u05);

-- 3) 树洞帖子
SET @u01 := (SELECT `id` FROM `user` WHERE `username` = 'demo01');
SET @u02 := (SELECT `id` FROM `user` WHERE `username` = 'demo02');
SET @u03 := (SELECT `id` FROM `user` WHERE `username` = 'demo03');
SET @u04 := (SELECT `id` FROM `user` WHERE `username` = 'demo04');
SET @u05 := (SELECT `id` FROM `user` WHERE `username` = 'demo05');

-- 每个用户只插一次：条件写成「这个用户还没有帖子」
SET @has01 := (SELECT COUNT(*) FROM `treehole_post` WHERE `user_id` = @u01);
SET @has02 := (SELECT COUNT(*) FROM `treehole_post` WHERE `user_id` = @u02);
SET @has03 := (SELECT COUNT(*) FROM `treehole_post` WHERE `user_id` = @u03);
SET @has04 := (SELECT COUNT(*) FROM `treehole_post` WHERE `user_id` = @u04);
SET @has05 := (SELECT COUNT(*) FROM `treehole_post` WHERE `user_id` = @u05);

INSERT INTO `treehole_post` (`user_id`, `content`, `like_count`, `comment_count`, `status`, `is_anonymous`, `created_at`)
SELECT @u01, '今天第一次在树洞里说话。其实白天笑得挺开心的，只是到了晚上就会想很多，不知道有没有人也这样。', 3, 1, 1, 1, DATE_SUB(NOW(), INTERVAL 26 HOUR)
FROM DUAL WHERE @u01 IS NOT NULL AND @has01 = 0;

INSERT INTO `treehole_post` (`user_id`, `content`, `like_count`, `comment_count`, `status`, `is_anonymous`, `created_at`)
SELECT @u02, '面试又没过。回家的地铁上一直想哭，人太多了，忍住了。', 5, 0, 1, 1, DATE_SUB(NOW(), INTERVAL 20 HOUR)
FROM DUAL WHERE @u02 IS NOT NULL AND @has02 = 0;

INSERT INTO `treehole_post` (`user_id`, `content`, `like_count`, `comment_count`, `status`, `is_anonymous`, `created_at`)
SELECT @u02, '有时候很想找个人说话，但打开对话框又不知道从哪句开始。', 1, 0, 1, 1, DATE_SUB(NOW(), INTERVAL 14 HOUR)
FROM DUAL WHERE @u02 IS NOT NULL AND @has02 = 0;

INSERT INTO `treehole_post` (`user_id`, `content`, `like_count`, `comment_count`, `status`, `is_anonymous`, `created_at`)
SELECT @u03, '和室友吵完架之后谁也没说话，屋里安静得有点可怕。', 2, 0, 1, 1, DATE_SUB(NOW(), INTERVAL 30 HOUR)
FROM DUAL WHERE @u03 IS NOT NULL AND @has03 = 0;

INSERT INTO `treehole_post` (`user_id`, `content`, `like_count`, `comment_count`, `status`, `is_anonymous`, `created_at`)
SELECT @u04, '今天在楼下看到一只很小的橘猫，它跟着我走了两步，又停下了。突然觉得被治愈了一点点。', 8, 0, 1, 0, DATE_SUB(NOW(), INTERVAL 9 HOUR)
FROM DUAL WHERE @u04 IS NOT NULL AND @has04 = 0;

INSERT INTO `treehole_post` (`user_id`, `content`, `like_count`, `comment_count`, `status`, `is_anonymous`, `created_at`)
SELECT @u05, '谢谢昨晚给我留言的人。本来只是随便写写，被回了一句之后，今天居然好过一点。', 4, 0, 1, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR)
FROM DUAL WHERE @u05 IS NOT NULL AND @has05 = 0;

-- 4) 给第一条帖子补几条评论（演示评论、回复与「评论区置顶」）
SET @post1 := (SELECT `id` FROM `treehole_post` WHERE `user_id` = @u01 ORDER BY `id` LIMIT 1);
INSERT INTO `post_comment` (`post_id`, `user_id`, `content`, `status`, `created_at`)
SELECT @post1, @u03, '我也这样，白天和晚上像两个人。你不是一个人。', 1, DATE_SUB(NOW(), INTERVAL 12 HOUR)
FROM DUAL WHERE @post1 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `post_comment` c WHERE c.`post_id` = @post1);

INSERT INTO `post_comment` (`post_id`, `user_id`, `content`, `status`, `created_at`)
SELECT @post1, @u02, '晚上确实会放大所有情绪，早点睡，明天会轻一点。', 1, DATE_SUB(NOW(), INTERVAL 10 HOUR)
FROM DUAL WHERE @post1 IS NOT NULL
  AND (SELECT COUNT(*) FROM `post_comment` c WHERE c.`post_id` = @post1) = 1;

INSERT INTO `post_comment` (`post_id`, `user_id`, `content`, `status`, `created_at`)
SELECT @post1, @u04, '抱一下，谢谢你愿意写出来。', 1, DATE_SUB(NOW(), INTERVAL 8 HOUR)
FROM DUAL WHERE @post1 IS NOT NULL
  AND (SELECT COUNT(*) FROM `post_comment` c WHERE c.`post_id` = @post1) = 2;

-- 5) 评论数按实际行数回填，避免人工填错
UPDATE `treehole_post` p
SET p.`comment_count` = (SELECT COUNT(*) FROM `post_comment` c
                         WHERE c.`post_id` = p.`id` AND c.`status` = 1)
WHERE p.`user_id` IN (@u01, @u02, @u03, @u04, @u05);

-- 6) 评论区置顶示例：demo01（贴主）把 demo03 的留言置顶
--    （贴主可以置顶任意一条评论，不限于是自己发的；每个帖子最多一条）
UPDATE `post_comment` SET `pinned_at` = DATE_SUB(NOW(), INTERVAL 2 HOUR)
WHERE `pinned_at` IS NULL
  AND `post_id` = @post1
  AND `content` LIKE '我也这样，白天和晚上像两个人%';

-- 7) v2.4.1 追加的一批日记 / 树洞
--    每次发版都会往这里补一段新内容（含危机关键词的日记与帖子，方便演示危机预警弹窗），
--    时间都是相对 NOW() 的，所以「日/周」趋势每次看起来都是新鲜的。
--    幂等靠「标志性内容是否已存在」判断，重复执行不会重复插入。
SET @u01 := (SELECT `id` FROM `user` WHERE `username` = 'demo01');
SET @u02 := (SELECT `id` FROM `user` WHERE `username` = 'demo02');
SET @u03 := (SELECT `id` FROM `user` WHERE `username` = 'demo03');
SET @u04 := (SELECT `id` FROM `user` WHERE `username` = 'demo04');
SET @u05 := (SELECT `id` FROM `user` WHERE `username` = 'demo05');

SET @n241_01 := (SELECT COUNT(*) FROM `diary` WHERE `user_id` = @u01 AND `content` LIKE '把积压的消息一条条回完%');
INSERT INTO `diary` (`user_id`, `content`, `emotion_score`, `emotion_label`, `weather`, `scene`, `privacy`, `ai_reply`, `ai_status`, `created_at`)
SELECT @u01, x.content, x.score, x.label, x.weather, x.scene, 'PRIVATE', x.reply, 'DONE',
       DATE_SUB(NOW(), INTERVAL x.hours HOUR)
FROM (
    SELECT '把积压的消息一条条回完，抬头发现天已经黑了，但心里挺踏实的。' AS content, 7 AS score,
           '开心' AS label, '晴天' AS weather, '工作' AS scene,
           '把欠着的事清掉，比做新的事更让人松一口气。今晚可以不用再列清单了。' AS reply,
           2 AS hours
    UNION ALL SELECT '早上去公园坐了一会儿，看人遛狗，什么都没想。', 8, '平静', '晴天', NULL,
           '什么都不想的那段时间，其实是大脑在替你还债。可以记住这个公园，它对你有效。', 27
) x
WHERE @u01 IS NOT NULL AND @n241_01 = 0;

SET @n241_02 := (SELECT COUNT(*) FROM `diary` WHERE `user_id` = @u02 AND `content` LIKE '今天被拒了一封邮件之后%');
INSERT INTO `diary` (`user_id`, `content`, `emotion_score`, `emotion_label`, `weather`, `scene`, `privacy`, `ai_reply`, `ai_status`, `created_at`)
SELECT @u02, x.content, x.score, x.label, x.weather, x.scene, 'PRIVATE', x.reply, 'DONE',
       DATE_SUB(NOW(), INTERVAL x.hours HOUR)
FROM (
    SELECT '今天被拒了一封邮件之后，坐在工位上缓了很久，怕同事看出来。' AS content, 4 AS score,
           '低落' AS label, '阴天' AS weather, '工作' AS scene,
           '被拒绝之后还要装作没事，是双重消耗。今天不必强行振作，把这一段当作已经在撑的证据。' AS reply,
           4 AS hours
    UNION ALL SELECT '算了算这个月的开销，越算越睡不着。', 3, '焦虑', '阴天', '工作',
           '睡前算钱会把焦虑带到梦里。可以把它挪到白天，写下来，能改的改，不能改的先放着。', 50
) x
WHERE @u02 IS NOT NULL AND @n241_02 = 0;

SET @n241_03 := (SELECT COUNT(*) FROM `diary` WHERE `user_id` = @u03 AND `content` LIKE '室友主动跟我说话了%');
INSERT INTO `diary` (`user_id`, `content`, `emotion_score`, `emotion_label`, `weather`, `scene`, `privacy`, `ai_reply`, `ai_status`, `created_at`)
SELECT @u03, x.content, x.score, x.label, x.weather, x.scene, 'PRIVATE', x.reply, 'DONE',
       DATE_SUB(NOW(), INTERVAL x.hours HOUR)
FROM (
    SELECT '室友主动跟我说话了，还问我要不要一起吃。原来那件事已经过去了。' AS content, 8 AS score,
           '开心' AS label, '晴天' AS weather, '家庭' AS scene,
           '很多关系没有我们想得那么脆。今天这份松动，是你先忍住没把话说难换来的。' AS reply,
           22 AS hours
    UNION ALL SELECT '跑步坚持到了第二周，今天三公里中间只走了两次。', 7, '平静', '晴天', NULL,
           '第二次比第一次难，你已经过掉了那个坎。可以给下周定个很小的目标：三次就够。', 75
) x
WHERE @u03 IS NOT NULL AND @n241_03 = 0;

SET @n241_04 := (SELECT COUNT(*) FROM `diary` WHERE `user_id` = @u04 AND `content` LIKE '这两天总在想自己是不是多余的%');
INSERT INTO `diary` (`user_id`, `content`, `emotion_score`, `emotion_label`, `weather`, `scene`, `privacy`, `ai_reply`, `ai_status`, `created_at`)
SELECT @u04, x.content, x.score, x.label, x.weather, x.scene, 'PRIVATE', x.reply, 'DONE',
       DATE_SUB(NOW(), INTERVAL x.hours HOUR)
FROM (
    SELECT '这两天总在想自己是不是多余的，越想越觉得撑不下去了，也活着太累。' AS content, 2 AS score,
           '低落' AS label, '阴天' AS weather, '工作' AS scene,
           '很抱歉你现在这么难。先把今晚过完：找一个能说话的人，说一句「我需要有人陪一会儿」，就一句。' AS reply,
           8 AS hours
    UNION ALL SELECT '晒了一会儿太阳，好像没那么沉了。', 6, '平静', '晴天', NULL,
           '身体先暖起来，情绪常常会跟着松一点。明天同样的时间再去站一会儿。', 31
) x
WHERE @u04 IS NOT NULL AND @n241_04 = 0;

SET @n241_05 := (SELECT COUNT(*) FROM `diary` WHERE `user_id` = @u05 AND `content` LIKE '最近睡得很少%');
INSERT INTO `diary` (`user_id`, `content`, `emotion_score`, `emotion_label`, `weather`, `scene`, `privacy`, `ai_reply`, `ai_status`, `created_at`)
SELECT @u05, x.content, x.score, x.label, x.weather, x.scene, 'PRIVATE', x.reply, 'DONE',
       DATE_SUB(NOW(), INTERVAL x.hours HOUR)
FROM (
    SELECT '最近睡得很少，半夜醒了就再也睡不着，白天整个人像被抽空。' AS content, 4 AS score,
           '焦虑' AS label, '阴天' AS weather, '工作' AS scene,
           '睡不着的时候躺着数时间，只会更清醒。可以起身喝点水、写两行字，等困意回来再躺回去。' AS reply,
           3 AS hours
    UNION ALL SELECT '因为很小的事对同事发了火，事后自己也不好受。', 4, '愤怒', '雨天', '工作',
           '火气常常是累出来的，不是你变坏了。能事后不好受，说明你在意别人。', 58
) x
WHERE @u05 IS NOT NULL AND @n241_05 = 0;

-- 危机预警：给上面两条命中危机关键词的日记各补一条 PENDING 记录，
-- 管理员登录后就能看到「求助」角标与预警弹窗（弹窗只提示新出现的记录）。
SET @cd04 := (SELECT `id` FROM `diary` WHERE `user_id` = @u04 AND `content` LIKE '这两天总在想自己是不是多余的%' ORDER BY `id` LIMIT 1);
INSERT INTO `crisis_alert` (`user_id`, `target_type`, `target_id`, `keyword`, `content`, `status`, `created_at`)
SELECT @u04, 'DIARY', @cd04, '撑不下去',
       (SELECT `content` FROM `diary` WHERE `id` = @cd04), 'PENDING', DATE_SUB(NOW(), INTERVAL 8 HOUR)
FROM DUAL
WHERE @cd04 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `crisis_alert` a WHERE a.`target_type` = 'DIARY' AND a.`target_id` = @cd04);

-- 8) v2.4.1 追加的一批树洞帖子
SET @n241_p01 := (SELECT COUNT(*) FROM `treehole_post` WHERE `user_id` = @u01 AND `content` LIKE '今天把拖了很久的事做完了%');
SET @n241_p05 := (SELECT COUNT(*) FROM `treehole_post` WHERE `user_id` = @u05 AND `content` LIKE '半夜在地铁站口站了很久%');
INSERT INTO `treehole_post` (`user_id`, `content`, `like_count`, `comment_count`, `status`, `is_anonymous`, `created_at`)
SELECT @u01, '今天把拖了很久的事做完了，没人知道，但我想在这里说一声。', 2, 0, 1, 1, DATE_SUB(NOW(), INTERVAL 18 HOUR)
FROM DUAL WHERE @u01 IS NOT NULL AND @n241_p01 = 0;

SET @n241_p02 := (SELECT COUNT(*) FROM `treehole_post` WHERE `user_id` = @u02 AND `content` LIKE '今天被拒绝了%');
INSERT INTO `treehole_post` (`user_id`, `content`, `like_count`, `comment_count`, `status`, `is_anonymous`, `created_at`)
SELECT @u02, '今天被拒绝了，很平静地接受了，然后在地铁上偷偷难过。', 4, 0, 1, 1, DATE_SUB(NOW(), INTERVAL 6 HOUR)
FROM DUAL WHERE @u02 IS NOT NULL AND @n241_p02 = 0;

SET @n241_p03 := (SELECT COUNT(*) FROM `treehole_post` WHERE `user_id` = @u03 AND `content` LIKE '发现自己其实没有想象中那么孤单%');
INSERT INTO `treehole_post` (`user_id`, `content`, `like_count`, `comment_count`, `status`, `is_anonymous`, `created_at`)
SELECT @u03, '发现自己其实没有想象中那么孤单，只是不太会开口。', 3, 0, 1, 0, DATE_SUB(NOW(), INTERVAL 40 HOUR)
FROM DUAL WHERE @u03 IS NOT NULL AND @n241_p03 = 0;

SET @n241_p04 := (SELECT COUNT(*) FROM `treehole_post` WHERE `user_id` = @u04 AND `content` LIKE '有时候真的会觉得我是负担%');
INSERT INTO `treehole_post` (`user_id`, `content`, `like_count`, `comment_count`, `status`, `is_anonymous`, `created_at`)
SELECT @u04, '有时候真的会觉得我是负担，晚上一个人待着特别难熬。', 6, 0, 1, 1, DATE_SUB(NOW(), INTERVAL 10 HOUR)
FROM DUAL WHERE @u04 IS NOT NULL AND @n241_p04 = 0;

INSERT INTO `treehole_post` (`user_id`, `content`, `like_count`, `comment_count`, `status`, `is_anonymous`, `created_at`)
SELECT @u05, '半夜在地铁站口站了很久，觉得活下去太累了，写在这里好像好受一点。', 5, 0, 1, 1, DATE_SUB(NOW(), INTERVAL 2 HOUR)
FROM DUAL WHERE @u05 IS NOT NULL AND @n241_p05 = 0;

-- 危机预警：上面那两条树洞帖子也要各留一条 PENDING 记录
SET @cp04 := (SELECT `id` FROM `treehole_post` WHERE `user_id` = @u04 AND `content` LIKE '有时候真的会觉得我是负担%' ORDER BY `id` LIMIT 1);
INSERT INTO `crisis_alert` (`user_id`, `target_type`, `target_id`, `keyword`, `content`, `status`, `created_at`)
SELECT @u04, 'POST', @cp04, '我是负担',
       (SELECT `content` FROM `treehole_post` WHERE `id` = @cp04), 'PENDING', DATE_SUB(NOW(), INTERVAL 10 HOUR)
FROM DUAL
WHERE @cp04 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `crisis_alert` a WHERE a.`target_type` = 'POST' AND a.`target_id` = @cp04);

SET @cp05 := (SELECT `id` FROM `treehole_post` WHERE `user_id` = @u05 AND `content` LIKE '半夜在地铁站口站了很久%' ORDER BY `id` LIMIT 1);
INSERT INTO `crisis_alert` (`user_id`, `target_type`, `target_id`, `keyword`, `content`, `status`, `created_at`)
SELECT @u05, 'POST', @cp05, '活着太累',
       (SELECT `content` FROM `treehole_post` WHERE `id` = @cp05), 'PENDING', DATE_SUB(NOW(), INTERVAL 2 HOUR)
FROM DUAL
WHERE @cp05 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `crisis_alert` a WHERE a.`target_type` = 'POST' AND a.`target_id` = @cp05);

-- 9) 新帖子的几条新留言 + 一条置顶，保证树洞列表看着是活的
INSERT INTO `post_comment` (`post_id`, `user_id`, `content`, `status`, `created_at`)
SELECT @cp04, @u01, '你不是负担。看到这条的时候请先喝口热水，我们都在。', 1, DATE_SUB(NOW(), INTERVAL 9 HOUR)
FROM DUAL WHERE @cp04 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `post_comment` c WHERE c.`post_id` = @cp04);

INSERT INTO `post_comment` (`post_id`, `user_id`, `content`, `status`, `created_at`)
SELECT @cp04, @u03, '扛不住的时候可以说出来，不用自己撑着。', 1, DATE_SUB(NOW(), INTERVAL 8 HOUR)
FROM DUAL WHERE @cp04 IS NOT NULL
  AND (SELECT COUNT(*) FROM `post_comment` c WHERE c.`post_id` = @cp04) = 1;

INSERT INTO `post_comment` (`post_id`, `user_id`, `content`, `status`, `created_at`)
SELECT p.`id`, @u05, '我也是，被拒绝的时候还得笑着说没事。', 1, DATE_SUB(NOW(), INTERVAL 5 HOUR)
FROM `treehole_post` p
WHERE p.`user_id` = @u02 AND p.`content` LIKE '今天被拒绝了%'
  AND NOT EXISTS (SELECT 1 FROM `post_comment` c WHERE c.`post_id` = p.`id`);

-- 评论数回填（含新帖），并把 demo04 那条求助帖的第一条留言置顶给贴主看
UPDATE `treehole_post` p
SET p.`comment_count` = (SELECT COUNT(*) FROM `post_comment` c
                         WHERE c.`post_id` = p.`id` AND c.`status` = 1)
WHERE p.`user_id` IN (@u01, @u02, @u03, @u04, @u05);

UPDATE `post_comment` SET `pinned_at` = DATE_SUB(NOW(), INTERVAL 7 HOUR)
WHERE `pinned_at` IS NULL
  AND `post_id` = @cp04
  AND `content` LIKE '你不是负担%';

-- ============================================================================
-- 10) v3.0.0 追加：好友关系、私聊记录、一批新日记与树洞
--     同样用「标志性内容 / 关系是否已存在」做幂等判断，重复启动不会重复插入。
-- ============================================================================

-- 10.1 好友关系（双向各插一行）
--      demo01 ↔ demo02、demo01 ↔ demo03、demo04 ↔ demo05
SET @f12 := (SELECT COUNT(*) FROM `friend` WHERE `user_id` = @u01 AND `friend_id` = @u02);
INSERT INTO `friend` (`user_id`, `friend_id`, `created_at`)
SELECT @u01, @u02, DATE_SUB(NOW(), INTERVAL 6 DAY) FROM DUAL
WHERE @u01 IS NOT NULL AND @u02 IS NOT NULL AND @f12 = 0;
INSERT INTO `friend` (`user_id`, `friend_id`, `created_at`)
SELECT @u02, @u01, DATE_SUB(NOW(), INTERVAL 6 DAY) FROM DUAL
WHERE @u01 IS NOT NULL AND @u02 IS NOT NULL AND @f12 = 0;

SET @f13 := (SELECT COUNT(*) FROM `friend` WHERE `user_id` = @u01 AND `friend_id` = @u03);
INSERT INTO `friend` (`user_id`, `friend_id`, `created_at`)
SELECT @u01, @u03, DATE_SUB(NOW(), INTERVAL 4 DAY) FROM DUAL
WHERE @u01 IS NOT NULL AND @u03 IS NOT NULL AND @f13 = 0;
INSERT INTO `friend` (`user_id`, `friend_id`, `created_at`)
SELECT @u03, @u01, DATE_SUB(NOW(), INTERVAL 4 DAY) FROM DUAL
WHERE @u01 IS NOT NULL AND @u03 IS NOT NULL AND @f13 = 0;

SET @f45 := (SELECT COUNT(*) FROM `friend` WHERE `user_id` = @u04 AND `friend_id` = @u05);
INSERT INTO `friend` (`user_id`, `friend_id`, `created_at`)
SELECT @u04, @u05, DATE_SUB(NOW(), INTERVAL 3 DAY) FROM DUAL
WHERE @u04 IS NOT NULL AND @u05 IS NOT NULL AND @f45 = 0;
INSERT INTO `friend` (`user_id`, `friend_id`, `created_at`)
SELECT @u05, @u04, DATE_SUB(NOW(), INTERVAL 3 DAY) FROM DUAL
WHERE @u04 IS NOT NULL AND @u05 IS NOT NULL AND @f45 = 0;

-- 10.2 一条待处理的好友申请：demo05 申请加 demo01（登录 demo01 就能在「我的」看到角标）
INSERT INTO `friend_request` (`from_user_id`, `to_user_id`, `message`, `status`, `created_at`)
SELECT @u05, @u01, '在树洞里看到你那条「把拖了很久的事做完了」，想认识一下。', 'PENDING',
       DATE_SUB(NOW(), INTERVAL 5 HOUR)
FROM DUAL
WHERE @u01 IS NOT NULL AND @u05 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `friend_request` r
                  WHERE r.`from_user_id` = @u05 AND r.`to_user_id` = @u01 AND r.`status` = 'PENDING');

-- 10.3 私聊记录（demo01 ↔ demo02、demo01 ↔ demo03）
--      最后两条故意留成未读，这样 demo01 登录后「聊天」页会有未读角标。
SET @c12 := (SELECT COUNT(*) FROM `chat_message` WHERE `from_user_id` = @u01 AND `to_user_id` = @u02);
INSERT INTO `chat_message` (`from_user_id`, `to_user_id`, `content`, `read_at`, `created_at`)
SELECT @u01, @u02, x.content, x.read_at, DATE_SUB(NOW(), INTERVAL x.hours HOUR)
FROM (
    SELECT '你上次那条树洞我看了，被拒绝真的不用装作没事。' AS content,
           DATE_SUB(NOW(), INTERVAL 30 HOUR) AS read_at, 34 AS hours
    UNION ALL SELECT '嗯，那天有个人回我一句，我就好受一点了。', DATE_SUB(NOW(), INTERVAL 28 HOUR), 32
    UNION ALL SELECT '那就好。以后要是难受，直接跟我说，不用先组织语言。', DATE_SUB(NOW(), INTERVAL 27 HOUR), 31
) x
WHERE @u01 IS NOT NULL AND @u02 IS NOT NULL AND @c12 = 0;

SET @c21 := (SELECT COUNT(*) FROM `chat_message` WHERE `from_user_id` = @u02 AND `to_user_id` = @u01);
INSERT INTO `chat_message` (`from_user_id`, `to_user_id`, `content`, `read_at`, `created_at`)
SELECT @u02, @u01, x.content, x.read_at, DATE_SUB(NOW(), INTERVAL x.hours HOUR)
FROM (
    SELECT '好。谢谢你，其实我一直不太敢说这些。' AS content,
           DATE_SUB(NOW(), INTERVAL 26 HOUR) AS read_at, 26 AS hours
    UNION ALL SELECT '这周又加班到十点，回家路上有点想哭。', NULL, 3
    UNION ALL SELECT '但我在楼下站了一会儿，看到月亮挺圆的，就没那么想哭了。', NULL, 2
) x
WHERE @u01 IS NOT NULL AND @u02 IS NOT NULL AND @c21 = 0;

SET @c13 := (SELECT COUNT(*) FROM `chat_message` WHERE `from_user_id` = @u01 AND `to_user_id` = @u03);
INSERT INTO `chat_message` (`from_user_id`, `to_user_id`, `content`, `read_at`, `created_at`)
SELECT @u01, @u03, '你今天跑步了吗，说好的三次。', DATE_SUB(NOW(), INTERVAL 20 HOUR), DATE_SUB(NOW(), INTERVAL 22 HOUR)
FROM DUAL WHERE @u01 IS NOT NULL AND @u03 IS NOT NULL AND @c13 = 0;
INSERT INTO `chat_message` (`from_user_id`, `to_user_id`, `content`, `read_at`, `created_at`)
SELECT @u03, @u01, '跑了两次，第三次留到明天，别催我哈哈。', DATE_SUB(NOW(), INTERVAL 19 HOUR), DATE_SUB(NOW(), INTERVAL 20 HOUR)
FROM DUAL WHERE @u01 IS NOT NULL AND @u03 IS NOT NULL AND @c13 = 0;

-- 10.4 v3.0 追加的日记（时间相对 NOW()，日/周趋势每次演示都是新的）
SET @n300_01 := (SELECT COUNT(*) FROM `diary` WHERE `user_id` = @u01 AND `content` LIKE '用日历回看了这个月的心情%');
INSERT INTO `diary` (`user_id`, `content`, `emotion_score`, `emotion_label`, `weather`, `scene`, `privacy`, `ai_reply`, `ai_status`, `created_at`)
SELECT @u01, x.content, x.score, x.label, x.weather, x.scene, 'PRIVATE', x.reply, 'DONE',
       DATE_SUB(NOW(), INTERVAL x.hours HOUR)
FROM (
    SELECT '用日历回看了这个月的心情，才发现低的那几天都挤在一起，不是我变差了，是那几天事太多了。' AS content,
           7 AS score, '平静' AS label, '晴天' AS weather, NULL AS scene,
           '把情绪和发生的事放在一起看，比单看一天公平得多。你已经在用自己的方式复盘了。' AS reply,
           5 AS hours
    UNION ALL SELECT '给自己定了个小规矩：难过的时候先去楼下走十分钟。', 8, '开心', '晴天', NULL,
           '给情绪配一个具体动作，是很好用的办法。十分钟不需要意志力，只需要穿上鞋。', 29
) x
WHERE @u01 IS NOT NULL AND @n300_01 = 0;

SET @n300_02 := (SELECT COUNT(*) FROM `diary` WHERE `user_id` = @u02 AND `content` LIKE '把「我不行」改成「我这周不行」%');
INSERT INTO `diary` (`user_id`, `content`, `emotion_score`, `emotion_label`, `weather`, `scene`, `privacy`, `ai_reply`, `ai_status`, `created_at`)
SELECT @u02, x.content, x.score, x.label, x.weather, x.scene, 'PRIVATE', x.reply, 'DONE',
       DATE_SUB(NOW(), INTERVAL x.hours HOUR)
FROM (
    SELECT '把「我不行」改成「我这周不行」，念出来居然轻松一点。' AS content, 6 AS score,
           '平静' AS label, '阴天' AS weather, '工作' AS scene,
           '给难受加一个期限，是把自己从「永远」里救出来。这周过去了，评价也会变。' AS reply,
           6 AS hours
    UNION ALL SELECT '朋友约我周末出门，我第一次没有先拒绝。', 7, '开心', '晴天', '社交',
           '先答应再想办法，比先拒绝再后悔舒服得多。周末不需要表现得好，出现就够了。', 46
) x
WHERE @u02 IS NOT NULL AND @n300_02 = 0;

SET @n300_03 := (SELECT COUNT(*) FROM `diary` WHERE `user_id` = @u03 AND `content` LIKE '今天在树洞里回了别人的帖子%');
INSERT INTO `diary` (`user_id`, `content`, `emotion_score`, `emotion_label`, `weather`, `scene`, `privacy`, `ai_reply`, `ai_status`, `created_at`)
SELECT @u03, x.content, x.score, x.label, x.weather, x.scene, 'PRIVATE', x.reply, 'DONE',
       DATE_SUB(NOW(), INTERVAL x.hours HOUR)
FROM (
    SELECT '今天在树洞里回了别人的帖子，写完发现那几句话也是说给自己的。' AS content, 8 AS score,
           '开心' AS label, '晴天' AS weather, '社交' AS scene,
           '安慰别人的时候，语言常常先落到自己身上。可以把那几句话抄下来，留给难的那天。' AS reply,
           7 AS hours
    UNION ALL SELECT '室友问我要不要一起吃饭，我没犹豫就答应了。', 8, '开心', '晴天', '家庭',
           '这种不犹豫，是关系变安全的信号。可以去，也可以哪天不想去就说不去。', 33
) x
WHERE @u03 IS NOT NULL AND @n300_03 = 0;

SET @n300_04 := (SELECT COUNT(*) FROM `diary` WHERE `user_id` = @u04 AND `content` LIKE '早上醒来那十分钟最难熬%');
INSERT INTO `diary` (`user_id`, `content`, `emotion_score`, `emotion_label`, `weather`, `scene`, `privacy`, `ai_reply`, `ai_status`, `created_at`)
SELECT @u04, x.content, x.score, x.label, x.weather, x.scene, 'PRIVATE', x.reply, 'DONE',
       DATE_SUB(NOW(), INTERVAL x.hours HOUR)
FROM (
    SELECT '早上醒来那十分钟最难熬，脑子还没醒，情绪已经先到了。' AS content, 4 AS score,
           '低落' AS label, '阴天' AS weather, '工作' AS scene,
           '刚醒来的那十分钟是情绪最没防备的时候。可以先做一件不需要决定的事：开窗、洗脸、喝水。' AS reply,
           9 AS hours
    UNION ALL SELECT '晚上一个人待着的时候，会觉得自己是个累赘，好像没有我大家会更轻松。', 2, '低落', '阴天', NULL,
           '这种「我是负担」的感觉，是情绪在替你说话，不是事实在说话。现在请做一件事：给你信任的人发一条消息，只说一句「今天有点难」。' AS reply,
           1
) x
WHERE @u04 IS NOT NULL AND @n300_04 = 0;

-- demo04 的这条日记命中危机关键词，补一条 PENDING 预警
SET @cd04v3 := (SELECT `id` FROM `diary` WHERE `user_id` = @u04 AND `content` LIKE '晚上一个人待着的时候，会觉得自己是个累赘%' ORDER BY `id` LIMIT 1);
INSERT INTO `crisis_alert` (`user_id`, `target_type`, `target_id`, `keyword`, `content`, `status`, `created_at`)
SELECT @u04, 'DIARY', @cd04v3, '累赘',
       (SELECT `content` FROM `diary` WHERE `id` = @cd04v3), 'PENDING', DATE_SUB(NOW(), INTERVAL 1 HOUR)
FROM DUAL
WHERE @cd04v3 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `crisis_alert` a WHERE a.`target_type` = 'DIARY' AND a.`target_id` = @cd04v3);

SET @n300_05 := (SELECT COUNT(*) FROM `diary` WHERE `user_id` = @u05 AND `content` LIKE '半夜心慌的时候试着写下来%');
INSERT INTO `diary` (`user_id`, `content`, `emotion_score`, `emotion_label`, `weather`, `scene`, `privacy`, `ai_reply`, `ai_status`, `created_at`)
SELECT @u05, x.content, x.score, x.label, x.weather, x.scene, 'PRIVATE', x.reply, 'DONE',
       DATE_SUB(NOW(), INTERVAL x.hours HOUR)
FROM (
    SELECT '半夜心慌的时候试着写下来，写完手不抖了，原来我是在怕明天。' AS content, 5 AS score,
           '焦虑' AS label, '阴天' AS weather, NULL AS scene,
           '焦虑常常是「怕」穿了个白天的外衣。写下来就等于把它从身体里挪到了纸上。明天只安排一件必须做的事。' AS reply,
           4 AS hours
    UNION ALL SELECT '给家里打了个电话，妈妈听出我不对劲，说想回来就回来。', 7, '开心', '晴天', '家庭',
           '被听出来，是还有人在认真听你说话。回去不必解释清楚，回去这件事本身就算休息。', 38
) x
WHERE @u05 IS NOT NULL AND @n300_05 = 0;

-- 10.5 v3.0 追加的树洞帖子
SET @n300_p01 := (SELECT COUNT(*) FROM `treehole_post` WHERE `user_id` = @u01 AND `content` LIKE '今天在地铁上给一个陌生人让了座%');
INSERT INTO `treehole_post` (`user_id`, `content`, `like_count`, `comment_count`, `status`, `is_anonymous`, `created_at`)
SELECT @u01, '今天在地铁上给一个陌生人让了座，他跟我说了谢谢，我一整天心情都不错。', 3, 0, 1, 1, DATE_SUB(NOW(), INTERVAL 12 HOUR)
FROM DUAL WHERE @u01 IS NOT NULL AND @n300_p01 = 0;

SET @n300_p02 := (SELECT COUNT(*) FROM `treehole_post` WHERE `user_id` = @u02 AND `content` LIKE '换了新工作三个月了%');
INSERT INTO `treehole_post` (`user_id`, `content`, `like_count`, `comment_count`, `status`, `is_anonymous`, `created_at`)
SELECT @u02, '换了新工作三个月了，还是会在开会前紧张到手心出汗，是不是我太慢了。', 2, 0, 1, 0, DATE_SUB(NOW(), INTERVAL 16 HOUR)
FROM DUAL WHERE @u02 IS NOT NULL AND @n300_p02 = 0;

SET @n300_p03 := (SELECT COUNT(*) FROM `treehole_post` WHERE `user_id` = @u03 AND `content` LIKE '开始记录每天有没有出门%');
INSERT INTO `treehole_post` (`user_id`, `content`, `like_count`, `comment_count`, `status`, `is_anonymous`, `created_at`)
SELECT @u03, '开始记录每天有没有出门，连续 12 天了，想在这里偷偷庆祝一下。', 5, 0, 1, 1, DATE_SUB(NOW(), INTERVAL 24 HOUR)
FROM DUAL WHERE @u03 IS NOT NULL AND @n300_p03 = 0;

SET @n300_p04 := (SELECT COUNT(*) FROM `treehole_post` WHERE `user_id` = @u04 AND `content` LIKE '今天一个人在家，一整天没跟人说话%');
INSERT INTO `treehole_post` (`user_id`, `content`, `like_count`, `comment_count`, `status`, `is_anonymous`, `created_at`)
SELECT @u04, '今天一个人在家，一整天没跟人说话，晚上突然很想有人能问一句我怎么样。', 4, 0, 1, 1, DATE_SUB(NOW(), INTERVAL 20 HOUR)
FROM DUAL WHERE @u04 IS NOT NULL AND @n300_p04 = 0;

SET @n300_p05 := (SELECT COUNT(*) FROM `treehole_post` WHERE `user_id` = @u05 AND `content` LIKE '刚看见有人在这个树洞里回我的帖子%');
INSERT INTO `treehole_post` (`user_id`, `content`, `like_count`, `comment_count`, `status`, `is_anonymous`, `created_at`)
SELECT @u05, '刚看见有人在这个树洞里回我的帖子，说「我们都在」，我盯着那句话看了很久。', 7, 0, 1, 1, DATE_SUB(NOW(), INTERVAL 44 HOUR)
FROM DUAL WHERE @u05 IS NOT NULL AND @n300_p05 = 0;

-- 10.6 新帖子的评论 + 评论数回填
SET @np04 := (SELECT MAX(`id`) FROM `treehole_post` WHERE `user_id` = @u04 AND `content` LIKE '今天一个人在家，一整天没跟人说话%');
INSERT INTO `post_comment` (`post_id`, `user_id`, `content`, `status`, `created_at`)
SELECT @np04, @u01, '你现在这样问出来了，就是有人在听。今天怎么样？', 1, DATE_SUB(NOW(), INTERVAL 19 HOUR)
FROM DUAL WHERE @np04 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `post_comment` c WHERE c.`post_id` = @np04);

SET @np05 := (SELECT MAX(`id`) FROM `treehole_post` WHERE `user_id` = @u05 AND `content` LIKE '刚看见有人在这个树洞里回我的帖子%');
INSERT INTO `post_comment` (`post_id`, `user_id`, `content`, `status`, `created_at`)
SELECT @np05, @u02, '那句话也有人想对你说。', 1, DATE_SUB(NOW(), INTERVAL 40 HOUR)
FROM DUAL WHERE @np05 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `post_comment` c WHERE c.`post_id` = @np05);

SET @np02 := (SELECT MAX(`id`) FROM `treehole_post` WHERE `user_id` = @u02 AND `content` LIKE '换了新工作三个月了%');
INSERT INTO `post_comment` (`post_id`, `user_id`, `content`, `status`, `created_at`)
SELECT @np02, @u03, '三个月还在紧张很正常，我第一年都这样。', 1, DATE_SUB(NOW(), INTERVAL 12 HOUR)
FROM DUAL WHERE @np02 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `post_comment` c WHERE c.`post_id` = @np02);

UPDATE `treehole_post` p
SET p.`comment_count` = (SELECT COUNT(*) FROM `post_comment` c
                         WHERE c.`post_id` = p.`id` AND c.`status` = 1)
WHERE p.`user_id` IN (@u01, @u02, @u03, @u04, @u05);

-- ============================================================
-- 11. v3.1 追加：带配图的树洞 + 站内通知
-- ============================================================
-- 配图用 frontend/public/demo 下的示例 SVG，随前端一起打包，不依赖 uploads 目录，
-- 换台机器重新建库也能正常显示；用户自己上传的图才会落在 /uploads/treehole 下。

-- 11.1 三个带配图的帖子（其中两条匿名，一条实名的两条图，用来演示九宫格布局）
SET @n310_p01 := (SELECT COUNT(*) FROM `treehole_post` WHERE `user_id` = @u02 AND `content` LIKE '下班路过河边%');
INSERT INTO `treehole_post` (`user_id`, `content`, `images`, `like_count`, `comment_count`, `status`, `is_anonymous`, `created_at`)
SELECT @u02, '下班路过河边，天是橘红色的，站着看了十分钟才想起要回家做饭。', '/demo/treehole-01.svg', 0, 0, 1, 0, DATE_SUB(NOW(), INTERVAL 6 HOUR)
FROM DUAL WHERE @u02 IS NOT NULL AND @n310_p01 = 0;

SET @n310_p02 := (SELECT COUNT(*) FROM `treehole_post` WHERE `user_id` = @u03 AND `content` LIKE '在公司待到十点%');
INSERT INTO `treehole_post` (`user_id`, `content`, `images`, `like_count`, `comment_count`, `status`, `is_anonymous`, `created_at`)
SELECT @u03, '在公司待到十点，整层楼只剩我这排灯还亮着。说不上难过，就是有点想被人看见。', '/demo/treehole-02.svg', 0, 0, 1, 1, DATE_SUB(NOW(), INTERVAL 10 HOUR)
FROM DUAL WHERE @u03 IS NOT NULL AND @n310_p02 = 0;

SET @n310_p03 := (SELECT COUNT(*) FROM `treehole_post` WHERE `user_id` = @u05 AND `content` LIKE '又到三点还醒着%');
INSERT INTO `treehole_post` (`user_id`, `content`, `images`, `like_count`, `comment_count`, `status`, `is_anonymous`, `created_at`)
SELECT @u05, '又到三点还醒着。窗外有辆车开过去，我就想，这个点也还有别人没睡。', '/demo/treehole-03.svg,/demo/treehole-01.svg', 0, 0, 1, 1, DATE_SUB(NOW(), INTERVAL 30 HOUR)
FROM DUAL WHERE @u05 IS NOT NULL AND @n310_p03 = 0;

-- 11.2 站内通知：给 demo01 造几条未读，进「消息通知」就能看到红点
SET @np310 := (SELECT MAX(`id`) FROM `treehole_post` WHERE `user_id` = @u01 AND `status` = 1);

INSERT INTO `notification` (`user_id`, `type`, `title`, `content`, `target_type`, `target_id`, `is_read`, `created_at`)
SELECT @u01, 'LIKE', '阿宁 赞了你的树洞', '今天终于把拖了两周的初稿写完了。虽然还很粗糙，但至少动起来了。',
       'POST', @np310, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR)
FROM DUAL WHERE @u01 IS NOT NULL AND @np310 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `notification` n
                  WHERE n.`user_id` = @u01 AND n.`type` = 'LIKE' AND n.`target_id` = @np310);

INSERT INTO `notification` (`user_id`, `type`, `title`, `content`, `target_type`, `target_id`, `is_read`, `created_at`)
SELECT @u01, 'COMMENT', '竹间 评论了你的树洞', '「至少动起来了」这句本身就很值得记一笔。',
       'POST', @np310, 0, DATE_SUB(NOW(), INTERVAL 3 HOUR)
FROM DUAL WHERE @u01 IS NOT NULL AND @np310 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `notification` n
                  WHERE n.`user_id` = @u01 AND n.`type` = 'COMMENT' AND n.`target_id` = @np310);

INSERT INTO `notification` (`user_id`, `type`, `title`, `content`, `target_type`, `target_id`, `is_read`, `created_at`)
SELECT @u01, 'FRIEND_REQUEST', '北岛 想加你为好友', '在树洞里看到你的帖子，想说声谢谢。',
       'USER', @u05, 0, DATE_SUB(NOW(), INTERVAL 2 HOUR)
FROM DUAL WHERE @u01 IS NOT NULL AND @u05 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `notification` n
                  WHERE n.`user_id` = @u01 AND n.`type` = 'FRIEND_REQUEST');

INSERT INTO `notification` (`user_id`, `type`, `title`, `content`, `target_type`, `target_id`, `is_read`, `created_at`)
SELECT @u01, 'CHAT', '阿宁 给你发来一条消息', '明天有空一起去河边走走吗？',
       'CONVERSATION', @u02, 1, DATE_SUB(NOW(), INTERVAL 26 HOUR)
FROM DUAL WHERE @u01 IS NOT NULL AND @u02 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `notification` n
                  WHERE n.`user_id` = @u01 AND n.`type` = 'CHAT');

-- 11.3 给 demo01 的几篇日记补上标签，让「情绪洞察」的标签维度有数据可看
SET @d165 := (SELECT `id` FROM `diary` WHERE `user_id` = @u01 AND `content` LIKE '今天终于把拖了两周的初稿写完了%' ORDER BY `id` LIMIT 1);
SET @d166 := (SELECT `id` FROM `diary` WHERE `user_id` = @u01 AND `content` LIKE '和朋友吃饭聊得挺开心%' ORDER BY `id` LIMIT 1);
SET @d167 := (SELECT `id` FROM `diary` WHERE `user_id` = @u01 AND `content` LIKE '加班到十点半%' ORDER BY `id` LIMIT 1);
SET @d180 := (SELECT `id` FROM `diary` WHERE `user_id` = @u01 AND `content` LIKE '把积压的消息一条条回完%' ORDER BY `id` LIMIT 1);

INSERT INTO `diary_tag` (`diary_id`, `tag_id`)
SELECT @d165, 1 FROM DUAL WHERE @d165 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `diary_tag` WHERE `diary_id` = @d165 AND `tag_id` = 1);
INSERT INTO `diary_tag` (`diary_id`, `tag_id`)
SELECT @d165, 12 FROM DUAL WHERE @d165 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `diary_tag` WHERE `diary_id` = @d165 AND `tag_id` = 12);
INSERT INTO `diary_tag` (`diary_id`, `tag_id`)
SELECT @d166, 7 FROM DUAL WHERE @d166 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `diary_tag` WHERE `diary_id` = @d166 AND `tag_id` = 7);
INSERT INTO `diary_tag` (`diary_id`, `tag_id`)
SELECT @d166, 16 FROM DUAL WHERE @d166 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `diary_tag` WHERE `diary_id` = @d166 AND `tag_id` = 16);
INSERT INTO `diary_tag` (`diary_id`, `tag_id`)
SELECT @d167, 6 FROM DUAL WHERE @d167 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `diary_tag` WHERE `diary_id` = @d167 AND `tag_id` = 6);
INSERT INTO `diary_tag` (`diary_id`, `tag_id`)
SELECT @d167, 13 FROM DUAL WHERE @d167 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `diary_tag` WHERE `diary_id` = @d167 AND `tag_id` = 13);
INSERT INTO `diary_tag` (`diary_id`, `tag_id`)
SELECT @d180, 1 FROM DUAL WHERE @d180 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `diary_tag` WHERE `diary_id` = @d180 AND `tag_id` = 1);
INSERT INTO `diary_tag` (`diary_id`, `tag_id`)
SELECT @d180, 13 FROM DUAL WHERE @d180 IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `diary_tag` WHERE `diary_id` = @d180 AND `tag_id` = 13);
