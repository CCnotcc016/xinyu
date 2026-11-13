package com.xinyu.util;

import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;

import java.util.Locale;
import java.util.Set;

/**
 * 密码强度策略：注册、改密码都要过这一关。
 *
 * <p>为什么单独抽一个类：以前只校验长度 6-32，123456 也能注册；
 * 校验规则分散在 DTO 注解里，改一处漏一处。这里统一成一份可测试的规则。</p>
 *
 * <p>注意只对「新设置的密码」生效，老账号（含演示账号）的历史密码不受影响，
 * 否则升级后所有人都登不进来。</p>
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 32;

    /** 各大泄露库里出现频率最高的几个，拦掉能挡掉很大一部分撞库 */
    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "12345678", "123456789", "1234567890", "87654321", "11111111",
            "password", "password1", "password123", "passw0rd", "qwerty123",
            "abc12345", "a1234567", "abcd1234", "1qaz2wsx", "1q2w3e4r",
            "iloveyou", "woaini1314", "5201314520", "admin123", "administrator",
            "xinyu123", "xinyu1234", "test1234", "changeme");

    private PasswordPolicy() {
    }

    /**
     * 不合规直接抛 {@link BusinessException}，message 是给用户看的具体原因，
     * 不用「密码不符合要求」这种说了等于没说的话。
     */
    public static void assertStrong(String username, String password) {
        if (password == null || password.isBlank()) {
            fail("请输入密码");
        }
        if (password.length() < MIN_LENGTH) {
            fail("密码至少 " + MIN_LENGTH + " 位");
        }
        if (password.length() > MAX_LENGTH) {
            fail("密码不能超过 " + MAX_LENGTH + " 位");
        }
        if (!isPrintableAscii(password)) {
            fail("密码只能使用英文字母、数字和常见符号");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            fail("密码需要同时包含字母和数字");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (WEAK_PASSWORDS.contains(lower)) {
            fail("这个密码太常见了，换一个");
        }
        if (username != null && username.length() >= 3 && lower.contains(username.toLowerCase(Locale.ROOT))) {
            fail("密码里不能包含用户名");
        }
        if (allSameChar(password)) {
            fail("密码不能是同一个字符重复");
        }
    }

    /** BCrypt 只认 72 字节，非 ASCII 还会被截断，索性只允许可见 ASCII */
    private static boolean isPrintableAscii(String password) {
        for (char c : password.toCharArray()) {
            if (c < 33 || c > 126) {
                return false;
            }
        }
        return true;
    }

    private static boolean allSameChar(String password) {
        char first = password.charAt(0);
        for (int i = 1; i < password.length(); i++) {
            if (password.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }

    private static void fail(String message) {
        throw new BusinessException(ErrorCode.PASSWORD_TOO_WEAK.getCode(), message);
    }
}
