package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String avatar;
    /** 手机号，注册必填；老账号可后补。仅用于账号找回与危机干预联系 */
    private String phone;
    /** 已同意的协议版本，NULL 表示从未同意过（v2.3 之前的账号） */
    private String agreementVersion;
    private LocalDateTime agreementAcceptedAt;
    /** 是否授权用定位换天气：1 同意 0 未同意或已撤回。与手机号一样，属于「用户明确给过的许可」 */
    private Integer locationConsent;
    private LocalDateTime locationConsentAt;
    private String role;
    private Integer status;
    /** 连续登录失败次数，登录成功清零 */
    private Integer failedAttempts;
    /** 登录锁定到期时间，NULL 表示未锁定 */
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt;
    // 同 Diary：交给数据库的 ON UPDATE CURRENT_TIMESTAMP 维护，避免旧值写回后时间戳不动
    @TableField(value = "updated_at", updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
