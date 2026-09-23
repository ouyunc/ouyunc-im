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
    public Mono<Boolean> replayOnline(Packet packet) {
        return processMono(packet);
    }

    @Override
    public Mono<Boolean> processMono(Packet packet) {
        // 离线返回成功。在线查询或广播抛错时整段失败，入口不提交幂等。
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
                log.debug("HTTP 推送 SERVER_NOTIFY 接收方不在线, to={}", message.getTo());
                return true;
            }
            MessageHelper.asyncSendMessage(packet, targets);
            return true;
        });
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
