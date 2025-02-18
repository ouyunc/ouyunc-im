package com.ouyunc.message.processor;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.QosLevelEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.core.listener.event.SaveMessageEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.validator.AuthValidator;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;


/**
 * 群聊消息处理器
 */
public class GroupMessageProcessor extends AbstractMessageProcessor<Byte>{
    private static final Logger log = LoggerFactory.getLogger(GroupMessageProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.GROUP;
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
        // 校验是否拥有相关权限 permission （是否被拉黑，禁用等）

    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.info("One2OneMessageProcessor 正在处理群组消息...");
        AbstractBaseProcessor<? extends Number> contentProcessor = MessageServerContext.messageContentProcessorCache.get(packet.getMessage().getContentType());
        if (contentProcessor != null) {
            contentProcessor.process(ctx, packet);
            return;
        }
        // 走默认内容处理逻辑
        // do something
        // 发送消息到对方
        Message message = packet.getMessage();
        // 获取群组成员登录标识id
        Set<String> groupUserIdentitySet = repository().groupUsersIdentity(packet);
        if (CollectionUtils.isEmpty(groupUserIdentitySet)) {
            log.error("群组：{}, 不存在群成员！群消息： {}", message.getTo(), packet);
            return;
        }
        // 如果不需要QOS，或者该消息qos 等于0，则直接返回,则不需要存储到离线数据，则是需要存储一份群会话消息；这种情况走读扩散
        if (!MessageContext.messageProperties.isQosEnable() || message.getQos() == QosLevelEnum.QOS_0.getLevel()) {
            // 保存消息， 30 天 过期， 后面通过配置文件进行可配置
            if (!repository().saveMessage(packet, message.getTo(), NumberConstant.NUMBER_30 * MessageConstant.DAY_TIMESTAMP)) {
                log.error("群消息: {} 保存非qos质量消息异常", packet);
                MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,"保存群息异常!", packet), true);
                return;
            }
        }else {
            // 如果开启了QOS，且该消息需要QOS，则需要存储离线消息给到每个群成员；注意这种情况下就走写扩散了
            // 获取群成员的唯一登录标识，进行发送消息 @todo 使用lua 脚本批量执行
            // 批量保存消息
            if (!repository().saveMessage(packet, message.getTo(), NumberConstant.NUMBER_30 * MessageConstant.DAY_TIMESTAMP)) {
                log.error("群消息: {} 保存qos质量消息异常", packet);
                MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,"保存群息异常!", packet), true);
                return;
            }
        }
        // 保存到磁盘
        MessageServerContext.publishEvent(new SaveMessageEvent(packet), true);

        // 做其他业务处理，可以让其他实现方式来处理


        // 保存成功后，进行发送消息
        for (String groupUserIdentity : groupUserIdentitySet) {
            // 获取群成员的唯一登录标识，进行发送消息
            List<LoginClientInfo> groupUserLoginClientInfos = ClientHelper.onlineAll(message.getMetadata().getAppKey(), groupUserIdentity);
            // 如果不在线的话，先保存到离线消息队列中，然后发送消息到对方
            if (CollectionUtils.isEmpty(groupUserLoginClientInfos)) {
                log.warn("发送消息到: {} 失败, 对方不在线!,消息已存储到离线队列中", message.getTo());
                continue;
            }
            // 异步给群成员的每个在线段发送消息
            MessageHelper.asyncSendMessage(packet, groupUserLoginClientInfos);
        }
    }
}
