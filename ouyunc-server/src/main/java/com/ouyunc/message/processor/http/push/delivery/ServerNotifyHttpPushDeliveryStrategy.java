package com.ouyunc.message.processor.http.push.delivery;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.PushTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.processor.http.push.HttpPushValidatorChain;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * HTTP 推送：系统通知投递（单播 / 广播）。
 */
public final class ServerNotifyHttpPushDeliveryStrategy implements HttpProcessor {

    public static final ServerNotifyHttpPushDeliveryStrategy INSTANCE = new ServerNotifyHttpPushDeliveryStrategy();

    private static final Logger log = LoggerFactory.getLogger(ServerNotifyHttpPushDeliveryStrategy.class);

    private ServerNotifyHttpPushDeliveryStrategy() {
    }

    @Override
    public MessageTypeEnum messageType() {
        return MessageTypeEnum.SERVER_NOTIFY;
    }

    @Override
    public void preProcess(Packet packet) throws HttpPipelineException {
        HttpPushValidatorChain.verifyServerNotify(packet);
    }

    @Override
    public void process(Packet packet) {
        HttpPushDeliverySupport.subscribeDelivery(packet, doProcess(packet));
    }

    private Mono<Boolean> doProcess(Packet packet) {
        // preProcess 已在幂等占位前完成
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        if (metadata == null) {
            log.warn("HTTP 推送 SERVER_NOTIFY 缺少 metadata: {}", packet);
            return Mono.just(false);
        }
        String appKey = metadata.getAppKey();
        if (isBroadcast(packet)) {
            return Mono.fromCallable(() -> {
                ClientHelper.broadcastServerNotify(appKey, packet);
                return true;
            });
        }
        return Mono.fromCallable(() -> {
            List<LoginClientInfo> targets = ClientHelper.onlineAll(appKey, message.getTo());
            if (CollectionUtils.isEmpty(targets)) {
                return failOffline(packet, "接收方不在线");
            }
            MessageHelper.asyncSendMessage(packet, targets);
            return true;
        });
    }

    /** 在线投递无目标：已 ACCEPTED 时保留幂等，后台记失败即可。 */
    private static boolean failOffline(Packet packet, String reason) {
        log.debug("HTTP 推送 SERVER_NOTIFY {}: {}", reason, packet.getMessage().getTo());
        return false;
    }

    private static boolean isBroadcast(Packet packet) {
        Metadata metadata = packet.getMessage().getMetadata();
        if (metadata == null || metadata.getHttpPushType() == null) {
            return false;
        }
        PushTypeEnum pushType = PushTypeEnum.getPushTypeEnum(metadata.getHttpPushType());
        return pushType == PushTypeEnum.BROADCAST_SERVER_NOTIFY
                || MessageConstant.SPLAT.equals(packet.getMessage().getTo());
    }
}
