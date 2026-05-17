package com.ouyunc.message.intercept;

import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.QosLevelEnum;
import com.ouyunc.base.constant.enums.QosModeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.Order;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.intercept.AbstractMessageInterceptor;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.schedule.ScheduleTimer;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * qos 消息重试拦截器
 */
@Order(NumberConstant.NUMBER_100)
public class QosRetryMessageInterceptor extends AbstractMessageInterceptor {
    @Override
    public boolean preHandle(Packet packet, Target target) {
        return true;
    }

    @Override
    public void postHandle(Packet packet, Target target) {
        // 判断是否是服务端模式
        // 判断是否需要qos
        if (MessageContext.isQosEnable() && MessageServerContext.serverProperties().isQosRetryEnable()) {
            Message message = packet.getMessage();
            Metadata metadata = message.getMetadata();
            int qos = message.getQos();
            // 如果消息qos的级别不等于0, 目前qos = 1,2,3 没做区分
            if (QosModeEnum.SERVER.equals(MessageServerContext.serverProperties().getQosMode()) && qos > QosLevelEnum.QOS_0.getLevel()) {
                // 这里clone 一个package,目的是防止qosPostHandle后面的逻辑对package的操作影响到定时发送的package
                Packet schedulePackage = packet.clone();
                ScheduleTimer.scheduleWithFixedDelay(String.valueOf(packet.getPacketId()), taskWrapper -> {
                    // 获取最终目标服务所有端的登录信息并组装成target, 只能在相同的appKey 下发送数据,
                    List<LoginClientInfo> targetLoginClientInfos = ClientHelper.onlineAll(metadata.getAppKey(), target.getTargetIdentity());
                    if (CollectionUtils.isEmpty(targetLoginClientInfos)) {
                        // 接收方全部离线时取消重试；上线后通过会话 ZSet + HTTP 分页拉取补数
                        taskWrapper.cancel();
                        return;
                    }
                    // 向所有在线端重试推送，直至任一端回 QOS_C2S_ACK（ScheduleTimer.cancel）或达到最大次数；客户端须按 packetId 幂等
                    for (LoginClientInfo targetLoginClientInfo : targetLoginClientInfos) {
                        MessageHelper.asyncSendMessage(schedulePackage, Target.newBuilder().targetIdentity(targetLoginClientInfo.getIdentity()).deviceType(targetLoginClientInfo.getDeviceType()).targetServerAddress(targetLoginClientInfo.getLoginServerAddress()).protocol(targetLoginClientInfo.getProtocol()).protocolVersion(targetLoginClientInfo.getProtocolVersion()).build());
                    }
                }, MessageServerContext.serverProperties().getQosRetryInitialDelay(), MessageServerContext.serverProperties().getQosRetryPeriod(), TimeUnit.SECONDS, MessageServerContext.serverProperties().getQosRetryMaxLoops());
            }
        }
    }

}
