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
    public boolean preHandle(Packet packet) {
        return true;
    }

    @Override
    public void postHandle(Packet packet) {
        // 判断是否是服务端模式
        // 判断是否需要qos
        if (MessageServerContext.serverProperties().isQosEnable() && MessageServerContext.serverProperties().isQosRetryEnable()) {
            Message message = packet.getMessage();
            Metadata metadata = message.getMetadata();
            int qos = message.getQos();
            // 如果消息qos的级别不等于0, 目前qos = 1,2,3 没做区分
            if (QosModeEnum.SERVER.equals(MessageServerContext.serverProperties().getQosMode()) && qos > QosLevelEnum.QOS_0.getLevel()) {
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
                }, MessageServerContext.serverProperties().getQosRetryInitialDelay(), MessageServerContext.serverProperties().getQosRetryPeriod(), TimeUnit.SECONDS, MessageServerContext.serverProperties().getQosRetryMaxLoops());
            }
        }
    }

}
