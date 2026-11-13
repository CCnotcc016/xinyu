package com.xinyu.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyu.entity.User;
import com.xinyu.mapper.UserMapper;
import com.xinyu.util.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 初始化管理员。
 *
 * <p>旧版本把 admin 账号（含密码哈希）直接写在 data.sql 里，README 还附了明文密码 ——
 * 只要脚本被跑过一次，任何看过仓库的人都能登进后台。现在改成：</p>
 * <ul>
 *   <li>只有在「一个可用管理员都没有」时才创建，绝不覆盖已有账号；</li>
 *   <li>密码来自 ADMIN_INIT_PASSWORD，必须满足 {@link PasswordPolicy}，
 *       不设或过弱时直接在启动日志里说清楚（弱密码不创建）；</li>
 *   <li>创建的账号 must_change_password=1，首次登录必须先改密码才能用其它接口。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${xinyu.admin.init-username:admin}")
    private String initUsername;

    @Value("${xinyu.admin.init-password:}")
    private String initPassword;

    @Override
    public void run(ApplicationArguments args) {
        Long admins = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "ADMIN")
                .eq(User::getStatus, 1));
        if (admins != null && admins > 0) {
            return;
        }
        String username = StringUtils.hasText(initUsername) ? initUsername.trim() : "admin";
        if (!StringUtils.hasText(initPassword)) {
            log.warn("""
                    库里还没有可用的管理员，但未设置 ADMIN_INIT_PASSWORD，已跳过初始化。
                    请在 .env 里设置 ADMIN_INIT_PASSWORD（8-32 位、含字母和数字）后重启，
                    或执行 scripts/init-admin.sh 手动创建。""");
            return;
        }
        try {
            PasswordPolicy.assertStrong(username, initPassword);
        } catch (RuntimeException e) {
            // 弱密码不落库：与其建一个后台入口给所有人猜，不如让运维改完再启动
            log.error("ADMIN_INIT_PASSWORD 不符合密码策略，未创建管理员：{}", e.getMessage());
            return;
        }
        User admin = new User();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(initPassword));
        admin.setNickname("系统管理员");
        admin.setRole("ADMIN");
        admin.setStatus(1);
        admin.setMustChangePassword(1);
        admin.setTokenVersion(0);
        userMapper.insert(admin);
        log.warn("已创建初始管理员 {}，首次登录必须先修改密码（must_change_password=1）", username);
    }
}
