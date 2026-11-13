package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户与管理员/客服的会话（一对一只保留一条，避免用户对话被拆散）。
 *
 * <p>{@code assignee_id} 为空表示「还没有客服接手」，所有客服都能看到并抢单；
 * 一旦有客服回过消息就固定归属，其他客服只读不到这个会话。</p>
 *
 * <p>客服「结单」后 {@code status} 变成 {@link #STATUS_CLOSED}，用户再来消息时会重新置为
 * {@link #STATUS_OPEN}、清空接手人（等新的客服/管理员接手），并把
 * {@code session_start_id} 顶到新消息前一条 —— 于是接手人只看得到本轮的内容，
 * 看不到结单之前的记录。</p>
 */
@Data
@TableName("support_conversation")
public class SupportConversation {

    /** 进行中 */
    public static final String STATUS_OPEN = "OPEN";
    /** 客服已结单，等用户再次咨询 */
    public static final String STATUS_CLOSED = "CLOSED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 接手的客服/管理员，NULL = 待接手 */
    private Long assigneeId;
    /** 最后一条消息摘要，列表页直接用，省一次查询 */
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    /** 用户未读条数（客服回复后 +1，用户打开会话清零） */
    private Integer userUnread;
    /** 客服侧未读条数（用户发消息后 +1，客服打开会话清零） */
    private Integer staffUnread;
    /** OPEN / CLOSED，见上面的常量 */
    private String status;
    private LocalDateTime closedAt;
    /** 结单的客服/管理员 */
    private Long closedById;
    /** 本轮接待的起始消息 ID，客服侧只展示 id 大于它的消息；0 表示不限 */
    private Long sessionStartId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
