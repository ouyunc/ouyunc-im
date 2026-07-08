package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.cs.CsImSessionRoute;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 客服聊天消息：仅维护 ticket 级最后一条消息（SLA 扫描用）。
 * <p>不再写入 session 级 lm；会话列表预览可走 ZSet 或 ticket lm。</p>
 */
public final class CsCustomerServiceLastMessageHelper {

    private CsCustomerServiceLastMessageHelper() {
    }

    public static void saveChatLastMessage(DefaultRepository repository, CsImSessionRoute route, Packet packet) {
        if (repository == null || route == null || packet == null) {
            return;
        }
        String ticketId = route.ticketId();
        if (StringUtils.isBlank(ticketId)) {
            return;
        }
        long expireMs = MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP;
        repository.saveLastMessageForCsTicket(ticketId.trim(), packet, expireMs, TimeUnit.MILLISECONDS);
    }
}
