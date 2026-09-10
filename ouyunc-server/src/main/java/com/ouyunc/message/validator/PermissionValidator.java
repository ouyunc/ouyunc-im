package com.ouyunc.message.validator;

import com.ouyunc.base.constant.enums.AppStatus;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.domain.entity.AppEntity;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 权限校验：应用存在且未停用；MQTT 可按配置关闭。通过返回 true。
 */
public enum PermissionValidator implements ReactiveValidator<Packet> {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(PermissionValidator.class);

    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        if (packet == null || packet.getMessage() == null) {
            return Mono.just(false);
        }
        if (packet.getProtocol() == ProtocolTypeEnum.MQTT.getProtocol()
                && !MessageServerContext.serverProperties().isMqttEnabled()) {
            log.warn("MQTT 已关闭，拒绝 packetId={}", packet.getPacketId());
            return Mono.just(false);
        }
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String appKey = metadata == null ? null : metadata.getAppKey();
        if (StringUtils.isBlank(appKey)) {
            return Mono.just(false);
        }
        return Mono.fromCallable(() -> {
                    AppEntity app = DefaultRepository.INSTANCE.getAppEntity(appKey);
                    if (app == null) {
                        log.warn("appKey:{} 不存在，拒绝消息", appKey);
                        return false;
                    }
                    if (AppStatus.ABNORMAL.value().equals(app.getStatus())) {
                        log.warn("appKey:{} 已停用，拒绝消息", appKey);
                        return false;
                    }
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.error("权限校验异常 appKey={}", appKey, e);
                    return Mono.just(false);
                });
    }
}
