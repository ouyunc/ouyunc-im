package com.ouyunc.message.processor;


import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MqttMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MqttMessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.validator.AuthValidator;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * @Author fzx
 * @Description: Mqtt 消息处理器
 **/
public final class MqttMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {


    private static final Logger log = LoggerFactory.getLogger(MqttMessageBiProcessor.class);



    @Override
    public MessageType type() {
        return MqttMessageTypeEnum.MQTT;
    }

    /***
     * @author fzx
     * @description 消息前置处理，做登录业务逻辑；通过后返回 true，不 fire
     */
    @Override
    public Mono<Boolean> preProcess(ChannelHandlerContext ctx, Packet packet) {
        // CONNECT 尚未登录，不归档；其它 MQTT 包认证通过后再归档
        if (MqttMessageContentTypeEnum.MQTT_CONNECT.getType() == packet.getMessage().getContentType()
                || MqttMessageContentTypeEnum.MQTT_PINGREQ.getType() == packet.getMessage().getContentType()) {
            return Mono.just(true);
        }
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
            ctx.close();
            return Mono.just(false);
        }
        return archiveAfterAuth(packet).thenReturn(true);
    }

    /***
     * @author fzx
     * @description 业务处理，登录消息不需要做任何处理
     */
    @Override
    public Mono<Void> process(ChannelHandlerContext ctx, Packet packet) {
        AbstractBaseBiProcessor<Mono<Void>, ? extends Number> mqttContentProcessor = MessageServerContext.messageContentProcessorCache.get(packet.getMessage().getContentType());
        if (mqttContentProcessor == null) {
            log.error("未找到对应的消息处理器，messageType= {}, 将关闭该连接!", packet.getMessageType());
            ctx.close();
            return Mono.empty();
        }
        return mqttContentProcessor.process(ctx, packet);
    }

}
