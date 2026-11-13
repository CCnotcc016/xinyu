package com.xinyu.security;

import com.xinyu.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 登录用户主体（适配 Spring Security）
 */
@Getter
public class LoginUser implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String role;
    private final boolean enabled;
    /** 签发 token 时带上的版本号，与库里不一致即视为已吊销 */
    private final int tokenVersion;
    /** 首次登录必须改密码（管理员初始化账号） */
    private final boolean mustChangePassword;

    public LoginUser(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.role = user.getRole();
        this.enabled = user.getStatus() != null && user.getStatus() == 1;
        this.tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        this.mustChangePassword = user.getMustChangePassword() != null && user.getMustChangePassword() == 1;
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
