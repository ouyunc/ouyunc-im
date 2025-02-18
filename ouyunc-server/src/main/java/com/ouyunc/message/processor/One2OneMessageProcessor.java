package com.ouyunc.message.processor;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.core.listener.event.SaveMessageEvent;
import com.ouyunc.core.listener.event.WithdrawMessageEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.validator.AuthValidator;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


/**
 * 一对一（单聊）消息处理器
 */
public class One2OneMessageProcessor extends AbstractMessageProcessor<Byte>{
    private static final Logger log = LoggerFactory.getLogger(One2OneMessageProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.ONE_2_ONE;
    }


    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 异步存储packet（目前只是保存相关信息，不做扩展，以后可以做数据分析使用），这里将该数据存储到时序数据库中
        messageProcessorExecutor.execute(() -> {
            repository().save(packet);
        });
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            // 关闭当前 channel，这里会触发 DefaultSocketChannelInitializer 中的关闭逻辑
            log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
            ctx.close();
            return;
        }
        // 校验是否拥有相关权限 permission （对方是否被拉黑，禁用等）

    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.info("One2OneMessageProcessor 正在处理一对一消息...");
        AbstractBaseProcessor<? extends Number> contentProcessor = MessageServerContext.messageContentProcessorCache.get(packet.getMessage().getContentType());
        if (contentProcessor != null) {
            contentProcessor.process(ctx, packet);
            return;
        }
        // 发送消息到对方
        Message message = packet.getMessage();
        // 保存消息， 30 天 过期， 后面通过配置文件进行可配置
        String sessionId = IdentityUtil.sessionId(message.getFrom(), message.getTo());
        if (!repository().saveMessage(packet, sessionId, NumberConstant.NUMBER_30 * MessageConstant.DAY_TIMESTAMP)) {
            log.error("一对一消息: {} 保存消息异常", packet);
            MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存一对一消息异常!", packet), true);
            return;
        }
        // 保存到磁盘
        MessageServerContext.publishEvent(new SaveMessageEvent(packet), true);


        // 可以做额外的业务处理, 比如这里将消息撤回，已读，撤销已读做特殊处理;
        if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == message.getContentType()) {
            // 获取需要撤销的消息id，（这里使用String类型接收）
            List<String> packetIds = JSON.parseArray(message.getContent(), String.class);
            if (CollectionUtils.isEmpty(packetIds)) {
                log.warn("处理撤销消息异常，packetIds: {} 为空", packetIds);
                return;
            }
            // 处理撤销消息
            if (!repository().withdrawMessage(packet, sessionId)) {
                // 未撤销成功
                log.error("处理撤销消息异常，packetIds: {}", packetIds);
                return;
            }
            // 撤销磁盘的消息
            MessageServerContext.publishEvent(new WithdrawMessageEvent(packet), true);
        }


        // 获取对方在线的所有客户端
        List<LoginClientInfo> toLoginClientInfos = ClientHelper.onlineAll(message.getMetadata().getAppKey(), message.getTo());
        // 如果不在线的话，先保存到离线消息队列中，然后发送消息到对方
        if (CollectionUtils.isEmpty(toLoginClientInfos)) {
            log.warn("发送消息到: {} 失败, 对方不在线!,消息已存储到离线队列中", message.getTo());
            return;
        }
        // 异步发送消息
        MessageHelper.asyncSendMessage(packet, toLoginClientInfos);
    }
}
