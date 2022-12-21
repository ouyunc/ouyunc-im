package com.ouyunc.im.processor;

import cn.hutool.json.JSONUtil;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.context.IMProcessContext;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.lock.DistributedLock;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.utils.SocketAddressUtil;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 加群申请，管理员同意，管理员拒绝，踢出群/退群/解散群/禁言（全部禁言/部分禁言）/黑名单
 */
public class GroupRequestMessageProcessor extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(GroupRequestMessageProcessor.class);

    @Override
    public MessageEnum messageType() {
        return  MessageEnum.IM_GROUP_REQUEST;
    }

    /**
     * 群相关请求
     * @param ctx
     * @param packet
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("GroupRequestMessageProcessor 正在处理好友请求消息packet: {}", packet);
        Message message = (Message) packet.getMessage();
        ExtraMessage extraMessage = JSONUtil.toBean(message.getExtra(), ExtraMessage.class);
        if (extraMessage == null) {
            extraMessage = new ExtraMessage();
        }
        // 根据to 群唯一标识，或客户端唯一标识
        String to = message.getTo();
        // 判断是否从其他服务路由过来的消息
        if (extraMessage.isDelivery()) {
            if (!IMServerContext.SERVER_CONFIG.isClusterEnable() || IMServerContext.SERVER_CONFIG.getLocalServerAddress().equals(extraMessage.getTargetServerAddress())) {
                MessageHelper.sendMessage(packet, IdentityUtil.generalComboIdentity(to, extraMessage.getDeviceEnum().getName()));
                return;
            }
            MessageHelper.deliveryMessage(packet, SocketAddressUtil.convert2SocketAddress(extraMessage.getTargetServerAddress()));
            return;
        }
        // 判断是什么类型的消息，好友申请，好友拒绝，好友同意
        IMProcessContext.MESSAGE_CONTENT_PROCESSOR.get(message.getContentType()).doProcess(ctx, packet);
    }
}