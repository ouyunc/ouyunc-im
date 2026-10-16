package com.ouyunc.message.processor.http.push.delivery;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MessageFromToTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.PushTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageDeliveryPlanner;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.processor.http.push.HttpPushValidatorChain;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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

    /** 与客服 CsImConstants 一致。这两类漏投会让对端关不了单，或坐席列表迟迟不出现访客。 */
    private static final String EVENT_SESSION_CLOSED = "cs.session.closed";
    private static final String EVENT_SESSION_ASSIGNED = "cs.session.assigned";

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
        // 输入中等可丢通知：离线仍返回成功。关单、以及发给坐席的分配：离线返回失败，调用方重试。
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        if (metadata == null) {
            log.warn("HTTP 推送 SERVER_NOTIFY 缺少 metadata: {}", packet);
            return Mono.just(false);
        }
        String appKey = metadata.getIngress().getAppKey();
        if (isBroadcast(packet)) {
            return Mono.fromCallable(() -> {
                ClientHelper.broadcastServerNotify(appKey, packet);
                return true;
            });
        }
        return Mono.fromCallable(() -> deliverUnicast(packet, appKey, message));
    }

    /**
     * 单播。只有关单、以及发给坐席的分配，才在负缓存未命中时再查一次 Redis。
     * 仍然找不到人就返回失败，客服侧用同一 messageId 重试。
     * 发给访客的 assigned 常发生在访客尚未连上 IM，离线按成功处理，避免每次进线都打成推送失败。
     * 输入中不再次穿透缓存，避免离线对端把 500ms 负缓存打穿成每次都打 Redis。
     */
    private static boolean deliverUnicast(Packet packet, String appKey, Message message) {
        String event = readEvent(message.getContent());
        boolean required = mustReachPeer(event, message.getToType());
        List<LoginClientInfo> targets = ClientHelper.onlineAll(appKey, message.getTo());
        if (CollectionUtils.isEmpty(targets) && required) {
            ClientHelper.invalidateRouteSnapshot(appKey, message.getTo());
            targets = ClientHelper.onlineAll(appKey, message.getTo());
        }
        if (CollectionUtils.isEmpty(targets)) {
            if (required) {
                log.warn("会话控制通知未投递，接收方不在线 event={} to={} packetId={}",
                        event, message.getTo(), packet.getPacketId());
                return false;
            }
            log.debug("HTTP 推送 SERVER_NOTIFY 接收方不在线, to={}", message.getTo());
            return true;
        }
        MessageDeliveryPlanner.deliverOnlineClients(packet, targets);
        return true;
    }

    private static boolean mustReachPeer(String event, int toType) {
        if (EVENT_SESSION_CLOSED.equals(event)) {
            return true;
        }
        return EVENT_SESSION_ASSIGNED.equals(event) && toType == MessageFromToTypeEnum.CS_AGENT.getType();
    }

    private static String readEvent(String content) {
        if (StringUtils.isBlank(content) || !content.trim().startsWith("{")) {
            return null;
        }
        try {
            JSONObject body = JSON.parseObject(content);
            return body == null ? null : StringUtils.trimToNull(body.getString("event"));
        } catch (RuntimeException ignored) {
            return null;
        }
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
}
