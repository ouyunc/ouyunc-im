package com.ouyunc.message.intercept;

import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.QosLevelEnum;
import com.ouyunc.base.constant.enums.QosModeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Order;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.intercept.AbstractMessageInterceptor;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.schedule.QosRetryTaskContext;
import com.ouyunc.message.schedule.QosRetryTaskIds;
import com.ouyunc.message.schedule.ScheduleTimer;
import com.ouyunc.repository.DefaultRepository;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * qos 消息重试拦截器：每个接收端（identity + deviceType）独立登记任务。
 */
@Order(NumberConstant.NUMBER_100)
public class QosRetryMessageInterceptor extends AbstractMessageInterceptor {

    private static final Logger log = LoggerFactory.getLogger(QosRetryMessageInterceptor.class);

    @Override
    public boolean preHandle(Packet packet, Target target) {
        return true;
    }

    @Override
    public void postHandle(Packet packet, Target target) {
        if (!MessageContext.isQosEnable() || !MessageServerContext.serverProperties().isQosRetryEnable()) {
            return;
        }
        Message message = packet.getMessage();
        if (message == null || message.getMetadata() == null || target == null) {
            return;
        }
        int qos = message.getQos();
        if (!QosModeEnum.SERVER.equals(MessageServerContext.serverProperties().getQosMode())
                || qos <= QosLevelEnum.QOS_0.getLevel()) {
            return;
        }
        String appKey = message.getMetadata().getAppKey();
        if (StringUtils.isBlank(appKey) || StringUtils.isBlank(target.getTargetIdentity())) {
            return;
        }
        QosRetryTaskContext retryContext = new QosRetryTaskContext(
                appKey,
                packet.getPacketId(),
                target.getAppKey(),
                target.getTargetIdentity(),
                target.getDeviceType()
        );
        String taskId = QosRetryTaskIds.build(retryContext);
        if (StringUtils.isBlank(taskId)) {
            return;
        }
        ScheduleTimer.scheduleWithFixedDelay(taskId, taskWrapper -> {
            LoginClientInfo device = ClientHelper.onlineDevice(
                    retryContext.targetAppKey(), retryContext.targetIdentity(), retryContext.deviceType());
            if (device == null) {
                // 该端离线：取消本端重试；上线后靠会话拉取补数
                taskWrapper.cancel();
                return;
            }
            Packet schedulePackage = loadRetryPacket(retryContext);
            if (schedulePackage == null) {
                log.warn("QoS 重试加载消息失败，取消任务: taskId={}", taskId);
                taskWrapper.cancel();
                return;
            }
            // 重推不再走拦截器，避免重复注册；只投本端
            MessageHelper.asyncSendMessageWithoutInterceptor(
                    schedulePackage.clone(),
                    MessageHelper.buildTarget(device));
        }, MessageServerContext.serverProperties().getQosRetryInitialDelay(),
                MessageServerContext.serverProperties().getQosRetryPeriod(),
                TimeUnit.SECONDS,
                MessageServerContext.serverProperties().getQosRetryMaxLoops());
    }

    private static Packet loadRetryPacket(QosRetryTaskContext retryContext) {
        List<Packet> packets = DefaultRepository.INSTANCE.getPackets(
                retryContext.appKey(),
                Collections.singletonList(retryContext.packetId()));
        if (CollectionUtils.isEmpty(packets)) {
            return null;
        }
        return packets.getFirst();
    }
}
