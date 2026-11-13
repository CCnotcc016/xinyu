package com.xinyu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyu.dto.PeerUnread;
import com.xinyu.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 私聊消息。两个人的会话没必要单开一张会话表：
 * 会话列表按「每对好友的最新一条消息」还原，未读数按发送者分组统计。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 会话列表：取我和每个人的最后一条消息。
     * IF(...) 把「对方」统一成 peer_id，再按 peer_id 取 MAX(id) 回表拿整行。
     */
    @Select("""
            SELECT m.* FROM chat_message m
            JOIN (
                SELECT IF(from_user_id = #{userId}, to_user_id, from_user_id) AS peer_id,
                       MAX(id) AS max_id
                FROM chat_message
                WHERE from_user_id = #{userId} OR to_user_id = #{userId}
                GROUP BY peer_id
            ) t ON m.id = t.max_id
            ORDER BY m.id DESC
            """)
    List<ChatMessage> selectConversations(@Param("userId") Long userId);

    /** 每个会话里我还没读的条数（只算别人发给我的） */
    @Select("""
            SELECT from_user_id AS peerId, COUNT(*) AS unread
            FROM chat_message
            WHERE to_user_id = #{userId} AND read_at IS NULL
            GROUP BY from_user_id
            """)
    List<PeerUnread> selectUnreadByPeer(@Param("userId") Long userId);

    /** 我未读的总条数（底部导航角标用） */
    @Select("SELECT COUNT(*) FROM chat_message WHERE to_user_id = #{userId} AND read_at IS NULL")
    int countUnread(@Param("userId") Long userId);

    /**
     * 两人之间的消息，beforeId 用于往上翻历史。
     * 用 id 而不是 offset，因为一边聊一边翻页时 offset 会错位。
     */
    @Select("""
            <script>
            SELECT * FROM chat_message
            WHERE ((from_user_id = #{me} AND to_user_id = #{peer})
                OR (from_user_id = #{peer} AND to_user_id = #{me}))
            <if test="beforeId != null"> AND id &lt; #{beforeId} </if>
            ORDER BY id DESC
            LIMIT #{size}
            </script>
            """)
    List<ChatMessage> selectPair(@Param("me") Long me, @Param("peer") Long peer,
                                 @Param("beforeId") Long beforeId, @Param("size") int size);

    @Select("""
            SELECT COUNT(*) FROM chat_message
            WHERE (from_user_id = #{me} AND to_user_id = #{peer})
               OR (from_user_id = #{peer} AND to_user_id = #{me})
            """)
    long countPair(@Param("me") Long me, @Param("peer") Long peer);

    /** 打开会话即把对方发来的消息标记为已读 */
    @Update("""
            UPDATE chat_message SET read_at = NOW()
            WHERE to_user_id = #{me} AND from_user_id = #{peer} AND read_at IS NULL
            """)
    int markRead(@Param("me") Long me, @Param("peer") Long peer);
}
