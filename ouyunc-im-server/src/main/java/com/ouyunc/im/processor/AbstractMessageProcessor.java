package com.ouyunc.im.processor;

import cn.hutool.json.JSONUtil;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.validate.MessageValidate;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

/**
 * @Author fangzhenxun
 * @Description: 消息抽象处理类
 * @Version V3.0
 **/
public abstract class AbstractMessageProcessor implements MessageProcessor {
    private static Logger log = LoggerFactory.getLogger(AbstractMessageProcessor.class);

    /**
     * 线程池事件执行器
     */
    public static final EventExecutorGroup EVENT_EXECUTORS = new DefaultEventExecutorGroup(16);

    /**
     * 标识子类处理消息的类型，如果一个子类处理多个类型使用 | 逻辑或进行返回
     */
    public abstract MessageEnum messageType();

    /**
     * @Author fangzhenxun
     * @Description 默认做鉴权处理，业务处理器可以不用重写该方法
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("现在处理默认的前置处理 packet: {} ...", packet);
        // 存储packet到数据库中（目前只是保存相关信息，不做扩展，以后可以做数据分析使用）
        EVENT_EXECUTORS.execute(() -> DbHelper.writeMessage(packet));
        Message message = (Message) packet.getMessage();
        if (!MessageValidate.isAuth(message.getFrom(), packet.getDeviceType(), ctx)) {
            return;
        }
        // 交给下个处理
        ctx.fireChannelRead(packet);
    }


    /**
     * 传递处理器
     * @param ctx
     * @param packet
     * @param function
     */
    protected void fireProcess(ChannelHandlerContext ctx, Packet packet, BiConsumer<ChannelHandlerContext, Packet> function) {
        log.info("正在使用默认处理器来处理消息");
        function.accept(ctx, packet);
        // 交给下个处理器去处理
        ctx.fireChannelRead(packet);
    }
    /**
     * @Author fangzhenxun
     * @Description 做默认后置处理, 默认处理时发送完消息后做给发送方做ack回应
     * 注意其他业务 处理器不用重写该postProcess, 如果重写请注意ack 的处理
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    public void postProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("现在处理默认的后置处理 packet: {} ...", packet);
        Message message = (Message) packet.getMessage();
        ExtraMessage extraMessage = JSONUtil.toBean(message.getExtra(), ExtraMessage.class);
        // 只在消息首次到达服务的地方发送ack给外部客户端
        if (IMServerContext.SERVER_CONFIG.isAcknowledgeModeEnable() && (extraMessage == null || !extraMessage.isDelivery())) {
            UserHelper.doReplyAck(message.getFrom(), packet);
        }
    }
}
