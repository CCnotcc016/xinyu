package com.xinyu.common;

/**
 * 法律文本的版本号。
 *
 * <p>《用户协议》《隐私政策》《免责声明》三份文本共用一个版本号，改动其中任何一份、
 * 或者改动政策里承诺过的数据处理方式（比如新增一种外部服务），都必须把版本号 +1。
 * 存量用户的 {@code agreement_version} 与这里不一致时，前端会强制弹「政策更新」弹窗，
 * 用户重新点过「我已阅读并同意」才能继续使用。</p>
 *
 * <p>前端的同名常量在 {@code frontend/src/config/legal.js}，两边必须保持一致。</p>
 */
public final class Legal {

    /** 现行版本，与 frontend/src/config/legal.js 的 LEGAL_VERSION 一一对应 */
    public static final String CURRENT_VERSION = "1.4";

    private Legal() {
    }
}
