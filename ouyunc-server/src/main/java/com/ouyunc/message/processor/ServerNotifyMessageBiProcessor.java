package com.ouyunc.message.processor;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.PushTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端通知消息处理器（MessageType=SERVER_NOTIFY）。
 * 供 HTTP 推送及内部系统通知复用；不经 {@code preProcess} 的 HTTP 入口会直接调用 {@link #process}。
 */
public final class ServerNotifyMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {

    private static final Logger log = LoggerFactory.getLogger(ServerNotifyMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.SERVER_NOTIFY;
    }

    @Override
    public Mono<Void> process(ChannelHandlerContext ctx, Packet packet) {
        return Mono.fromRunnable(() -> {
            Message message = packet.getMessage();
            if (message == null || message.getMetadata() == null) {
                log.warn("SERVER_NOTIFY 缺少 message/metadata: {}", packet);
                return;
            }
            String appKey = message.getMetadata().getIngress().getAppKey();
            if (isBroadcast(packet)) {
                ClientHelper.broadcastServerNotify(appKey, packet);
                return;
            }
            List<LoginClientInfo> targets = ClientHelper.onlineAll(appKey, message.getTo());
            if (CollectionUtils.isEmpty(targets)) {
                log.debug("SERVER_NOTIFY 接收方 {} 不在线", message.getTo());
                return;
            }
            if (isRemoteLogin(message)) {
                targets = filterKickDevice(targets, packet.getDeviceType());
            }
            MessageHelper.asyncSendMessage(packet, targets);
        });
    }

    private static boolean isBroadcast(Packet packet) {
        Metadata metadata = packet.getMessage().getMetadata();
        if (metadata == null || metadata.getIngress().getHttpPushType() == null) {
            return false;
        }
        PushTypeEnum pushType = PushTypeEnum.getPushTypeEnum(metadata.getIngress().getHttpPushType());
        return pushType == PushTypeEnum.BROADCAST_SERVER_NOTIFY
                || MessageConstant.SPLAT.equals(packet.getMessage().getTo());
    }

    private static boolean isRemoteLogin(Message message) {
        return message.getContentType() == MessageContentTypeEnum.REMOTE_LOGIN_CONTENT.getType();
    }

    /**
     * 顶号只踢对应设备；deviceType 对不上时回退全端，避免漏踢。
     */
    private static List<LoginClientInfo> filterKickDevice(List<LoginClientInfo> targets, byte deviceType) {
        List<LoginClientInfo> matched = new ArrayList<>();
        for (LoginClientInfo target : targets) {
            if (target != null && target.getDeviceType() == deviceType) {
                matched.add(target);
            }
        }
        return matched.isEmpty() ? targets : matched;
    }
}
