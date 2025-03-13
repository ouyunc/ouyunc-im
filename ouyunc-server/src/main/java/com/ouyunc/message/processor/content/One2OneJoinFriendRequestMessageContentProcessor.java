package com.ouyunc.message.processor.content;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.processor.AbstractBaseProcessor;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 加好友
 */
public class One2OneJoinFriendRequestMessageContentProcessor extends AbstractBaseProcessor<Integer> {
    private static final Logger log = LoggerFactory.getLogger(One2OneJoinFriendRequestMessageContentProcessor.class);

    @Override
    public MessageContentType type() {
        return MessageContentTypeEnum.FRIEND_REQUEST_JOIN_CONTENT;
    }

    @SuppressWarnings("unchecked")
    @Override
    public DefaultRepository repository() {
        return super.repository();
    }

    /**
     * 处理加好友请求
     * @param ctx
     * @param packet
     */
    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.info("One2OneJoinFriendRequestMessageContentProcessor 正在处理加好友请求 {} ...", packet);
        // 1. 保存消息
        Message message = packet.getMessage();
        String sessionId = IdentityUtil.sessionId(message.getFrom(), message.getTo());
        if (!repository().saveMessage(packet, sessionId, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)) {
            log.error("Failed to save one-to-one join friend request message: {}", packet);
            MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存一对一加好友请求消息异常!", packet), true);
            return;
        }
        // 如果接收方在线，则直接发送消息
        List<LoginClientInfo> toLoginClientInfos = ClientHelper.onlineAll(message.getMetadata().getAppKey(), message.getTo());
        if (CollectionUtils.isNotEmpty(toLoginClientInfos)) {
            MessageHelper.asyncSendMessage(packet, toLoginClientInfos);
        }
        // 处理成功则转到下个处理器
        ctx.fireChannelRead(packet);
    }
}
