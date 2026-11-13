package com.xinyu.common;

/**
 * 业务错误码
 */
public enum ErrorCode {
    SUCCESS(0, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    USERNAME_EXISTS(1001, "用户名已存在"),
    USERNAME_OR_PASSWORD_ERROR(1002, "用户名或密码错误"),
    USER_DISABLED(1003, "账号已被禁用"),
    USER_NOT_FOUND(1004, "用户不存在"),
    CANNOT_DISABLE_SELF(1005, "不能禁用当前登录的账号"),
    CANNOT_DELETE_SELF(1006, "不能删除当前登录的账号"),
    LAST_ADMIN(1007, "系统至少需要保留一个可用管理员"),
    PHONE_REQUIRED(1008, "请填写手机号"),
    PHONE_INVALID(1009, "手机号格式不正确"),
    PHONE_EXISTS(1010, "该手机号已被注册"),
    PHONE_SAME(1011, "新手机号与当前手机号相同"),
    PHONE_NOT_BOUND(1012, "当前账号尚未绑定手机号"),
    PASSWORD_WRONG(1013, "原密码不正确"),
    NICKNAME_FORBIDDEN(1014, "该昵称包含违规词，请换一个"),
    CAPTCHA_INVALID(1015, "人机验证已失效，请点击图片刷新"),
    CAPTCHA_WRONG(1016, "人机验证答案错误"),
    SMS_CODE_INVALID(1017, "验证码错误或已过期"),
    SMS_TOO_FREQUENT(1018, "验证码发送过于频繁，请稍后再试"),
    SMS_LIMIT_EXCEEDED(1019, "今日验证码发送次数已达上限"),
    UPLOAD_EMPTY(1020, "请选择要上传的文件"),
    UPLOAD_TYPE_UNSUPPORTED(1021, "仅支持 jpg / png / webp / gif 图片"),
    UPLOAD_TOO_LARGE(1022, "图片不能超过 2MB"),
    UPLOAD_FAILED(1023, "文件保存失败，请稍后再试"),
    PASSWORD_SAME(1024, "新密码不能与原密码相同"),
    ACCOUNT_LOCKED(1026, "密码错误次数过多，账号已被临时锁定"),
    PASSWORD_TOO_WEAK(1027, "密码强度不足"),
    POST_IMAGE_TOO_LARGE(1025, "单张图片不能超过 5MB"),
    DIARY_NOT_FOUND(2001, "日记不存在"),
    SUPPORT_CONVERSATION_NOT_FOUND(2002, "会话不存在或已被其他客服接手"),
    SUPPORT_CONVERSATION_CLOSED(2003, "该会话已结单，用户再次咨询后会重新分配"),
    SUPPORT_CLOSE_NOT_ASSIGNEE(2004, "只有接手的客服可以结单，请先回复一条消息"),
    POST_NOT_FOUND(3001, "帖子不存在"),
    COMMENT_NOT_FOUND(3002, "评论不存在"),
    REPORT_NOT_FOUND(3003, "举报记录不存在"),
    CRISIS_ALERT_NOT_FOUND(3004, "预警记录不存在"),
    POST_NOT_OWNED(3005, "只有贴主可以置顶评论"),
    FRIEND_SELF(3006, "不能把自己加为好友"),
    FRIEND_EXISTS(3007, "你们已经是好友了"),
    FRIEND_REQUEST_EXISTS(3008, "已经申请过了，等对方确认就好"),
    FRIEND_REQUEST_NOT_FOUND(3009, "申请不存在或已被处理"),
    FRIEND_REQUEST_NOT_TARGET(3010, "只能处理发给自己的申请"),
    NOT_FRIEND(3011, "成为好友后才能聊天"),
    SCALE_NOT_FOUND(3012, "量表不存在"),
    SCALE_ANSWERS_INVALID(3013, "请完成全部题目后再提交"),
    ASSESSMENT_NOT_FOUND(3014, "自评记录不存在"),
    RATE_LIMITED(429, "操作过于频繁，请稍后再试"),
    SENSITIVE_WORD(4001, "内容包含敏感词，请修改后重试"),
    AI_SERVICE_ERROR(5001, "AI 服务暂时不可用，请稍后再试"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
