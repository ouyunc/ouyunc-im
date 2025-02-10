package com.ouyunc.message.processor;

import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.qos.Qos;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.schedule.ScheduleTimer;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.Repository;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @Author fzx
 * @Description: 基础抽象处理类
 **/
public abstract class AbstractBaseProcessor<T extends Number> implements Processor<Packet>, Qos {
    private static final Logger log = LoggerFactory.getLogger(AbstractBaseProcessor.class);

    /**
     * 类型
     */
    public abstract Type<? extends T> type();

    /**
     * 获取数据存储实现类, 子类可以重写来实现自定义存储实现
     */
    @SuppressWarnings("unchecked")
    public <R extends Repository> R repository() {
        return (R) DefaultRepository.INSTANCE;
    }

    /**
     * qos 前置处理，一般用于消息过滤，比如消息是否是重发等
     */
    @Override
    public boolean qosPreHandle(ChannelHandlerContext ctx, Packet packet) {
        // 判断是否是需要qos以及是否是客户端模式
        Message message = packet.getMessage();
        int qos = message.getQos();
        if (qos > QosLevelEnum.QOS_0.getLevel()) {
            // 判断是否是客户端模式
            if (QosModeEnum.CLIENT.equals(MessageServerContext.serverProperties().getQosMode())) {
                // 如果是客户端模式，判断是否需要拦截（是否是重发消息），如果是重发消息且已经发送过（存储到离线消息中），则直接返回ack，否则构造正常消息，往下传递
                log.info("如果是客户端模式，判断是否需要拦截（是否是重发消息），如果是重发消息且已经发送过（存储到离线消息中），则直接返回ack，否则构造正常消息，往下传递");
            }
        }
        return true;
    }

    /**
     * qos 后置处理，一般用于发送ack，给发送端确认消息已经到达服务端
     */
    @Override
    public void qosPostHandle(ChannelHandlerContext ctx, Packet packet) {
        // 这里使用默认的ack
        // 判断是否是服务端模式
        // 判断是否需要qos
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        int qos = message.getQos();
        // 如果消息qos的级别不等于0, 目前qos = 1,2,3 没做区分
        if (qos > QosLevelEnum.QOS_0.getLevel()) {
            if (QosModeEnum.SERVER.equals(MessageServerContext.serverProperties().getQosMode())) {
                // 这里clone 一个package,目的是防止qosPostHandle后面的逻辑对package的操作影响到定时发送的package
                Packet schedulePackage = packet.clone();
                ScheduleTimer.scheduleWithFixedDelay(String.valueOf(packet.getPacketId()), taskWrapper -> {
                    // 获取最终目标服务所有端的登录信息并组装成target, 只能在相同的appKey 下发送数据,
                    List<LoginClientInfo> targetLoginClientInfos = ClientHelper.onlineAll(metadata.getAppKey(), message.getTo());
                    if (CollectionUtils.isEmpty(targetLoginClientInfos)) {
                        // 这里直接取消，因为消息已经存到离线队列中，等接收方上线后直接从离线消息拉取即可
                        taskWrapper.cancel();
                        return;
                    }
                    // 这里给所有端都重试发送？这里需要考虑一个题，针对多端的发送，一条数据如果某一个端或某几个端接收到了数据，是否要重复发送？这里只要有一个端发送成功则不再重试发送，会将待确认消息剔除，但是可能会出现数据重复发送的情况，需要做幂等
                    for (LoginClientInfo targetLoginClientInfo : targetLoginClientInfos) {
                        MessageHelper.asyncSendMessage(schedulePackage, Target.newBuilder().targetIdentity(targetLoginClientInfo.getIdentity()).deviceType(targetLoginClientInfo.getDeviceType()).targetServerAddress(targetLoginClientInfo.getLoginServerAddress()).build());
                    }
                }, NumberConstant.NUMBER_3, NumberConstant.NUMBER_3, TimeUnit.SECONDS, NumberConstant.NUMBER_3);
            }
            // 发送ack
            // 构造一个ack消息包
            Packet ackPacket = packet.clone();
            Message ackMessage = ackPacket.getMessage();
            String from = ackMessage.getFrom();
            ackMessage.setFrom(null);
            ackMessage.setTo(from);
            ackMessage.setContent(String.valueOf(packet.getPacketId()));
            ackMessage.setContentType(MessageContentTypeEnum.TEXT_CONTENT.getType());
            ackMessage.setCreateTime(TimeUtil.currentTimeMillis());
            ackPacket.setPacketId(MessageContext.<Long>idGenerator().generateId());
            ackPacket.setMessageType(MessageTypeEnum.QOS_S2C_ACK.getType());
            MessageHelper.asyncSendMessage(ackPacket, Target.newBuilder().targetIdentity(from).deviceType(MessageServerContext.deviceTypeCache.get(packet.getDeviceType())).targetServerAddress(MessageServerContext.serverProperties().getLocalServerAddress()).build());
        }
    }
}
