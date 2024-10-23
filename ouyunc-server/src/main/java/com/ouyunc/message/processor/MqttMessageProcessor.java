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

/**
 * @Author fzx
 * @Description: Mqtt 消息处理器
 **/
public class MqttMessageProcessor extends AbstractMessageProcessor<Byte> {


    private static final Logger log = LoggerFactory.getLogger(MqttMessageProcessor.class);



    @Override
    public MessageType type() {
        return MqttMessageTypeEnum.MQTT;
    }

    /***
     * @author fzx
     * @description 消息前置处理，做登录业务逻辑
     */
    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("正在预处理mqtt消息...");
        // 异步存储packet（目前只是保存相关信息，不做扩展，以后可以做数据分析使用），这里将该数据存储到时序数据库中
        messageProcessorExecutor.execute(() -> {
            repository().save(packet);
        });
        // 只处理鉴权消息，如果是不是连接connect则进行鉴权，鉴权通过往下走，是connect直接往下走
        if (MqttMessageContentTypeEnum.MQTT_CONNECT.getType() == packet.getMessage().getContentType()) {
            ctx.fireChannelRead(packet);
            return;
        }
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            // 关闭当前 channel，这里会触发 DefaultSocketChannelInitializer 中的关闭逻辑
            log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
            ctx.close();
            return;
        }
        // 交给下个处理
        ctx.fireChannelRead(packet);
    }

    /***
     * @author fzx
     * @description 业务处理，登录消息不需要做任何处理
     */
    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        MessageServerContext.messageContentProcessorCache.get(packet.getMessage().getContentType()).process(ctx, packet);
    }

}
