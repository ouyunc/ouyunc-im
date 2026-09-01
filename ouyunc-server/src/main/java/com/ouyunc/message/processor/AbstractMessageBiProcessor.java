package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.validator.AuthValidator;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 消息抽象处理类
 **/
public abstract class AbstractMessageBiProcessor<T extends Number> extends AbstractBaseBiProcessor<T> {

    private static final Logger log = LoggerFactory.getLogger(AbstractMessageBiProcessor.class);

    /**
     * 获取数据存储实现类, 子类可以重写来实现自定义存储实现
     */
    @SuppressWarnings("unchecked")
    public DefaultRepository repository() {
        return DefaultRepository.INSTANCE;
    }

    /**
     * @Author fzx
     * @Description 前置处理器，做认证授权相关处理，在真正处理消息前处理
     */
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 异步存储packet（目前只是保存相关信息，不做扩展，以后可以做数据分析使用），这里将该数据存储到时序数据库中
        repository().save(packet);
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            // 关闭当前 channel，这里会触发 DefaultSocketChannelInitializer 中的关闭逻辑
            log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
            MessageContext.publishEvent(new MessageEvent(
                    ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过", packet),
                    MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return;
        }
        // 校验是否拥有相关权限 permission

        // 做qos 处理（QOS_DUP 展开时在同一 packet 引用上原地更新）
        if (MessageContext.isQosEnable() && qosPreHandle(ctx, packet)) {
            return;
        }
        ctx.fireChannelRead(packet);
    }

    /**
     * @Author fzx
     * @Description 做后置处理：仅传递给下个 handler。
     *
     * <p><strong>注意</strong>：QoS ACK 不再在此发送。子类应在持久化成功后、在 {@link MessageContext#isQosEnable()} 为 true 时显式调用 {@link #qosPostHandle(io.netty.channel.ChannelHandlerContext, com.ouyunc.base.packet.Packet)}，
     * 以避免「ACK 已回但消息未持久化」导致客户端不重发而消息丢失的问题。</p>
     */
    public void postProcess(ChannelHandlerContext ctx, Packet packet) {
        ctx.fireChannelRead(packet);
    }
}
