package com.ouyunc.message.helper;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.exception.ExceptionReporter;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.repository.BindGroupResult;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 入群热写结果收口：容量拒绝走业务拒绝，写失败走 UNKNOWN。
 */
public final class GroupBindResultHelper {

    private static final Logger log = LoggerFactory.getLogger(GroupBindResultHelper.class);

    private GroupBindResultHelper() {
    }

    public static int maxMembers() {
        return MessageServerContext.serverProperties().getGroupMaxMembers();
    }

    public static int maxPerUser() {
        return MessageServerContext.serverProperties().getGroupMaxPerUser();
    }

    /**
     * @return true 已入群（含已是成员）；false 已向客户端回写拒绝或未知
     */
    public static boolean acceptedOrReply(ChannelHandlerContext ctx, Packet packet, BindGroupResult result,
                                          String failEventMessage) {
        if (result != null && result.accepted()) {
            return true;
        }
        if (result != null && result.capacityRejected()) {
            log.warn("入群容量拒绝 result={} packetId={}", result, packet == null ? null : packet.getPacketId());
            MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
            return false;
        }
        log.error("绑定群组失败: {}", packet);
        ExceptionReporter.reportSystem(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                failEventMessage != null ? failEventMessage : "绑定群组失败",
                "GroupBindResultHelper", packet);
        MessageSendResultHelper.unknown(ctx, packet, ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR);
        return false;
    }
}
