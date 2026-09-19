package com.ouyunc.message.intercept;

import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.model.Order;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.intercept.AbstractMessageInterceptor;
import com.ouyunc.message.schedule.QosRetryScheduler;

/**
 * 单 Target 投递后在始发节点登记 SERVER QoS 下行重试。
 * 群/私聊主路径走 {@code asyncSendMessage(packet, clients)} 扇出，由
 * {@link QosRetryScheduler#scheduleForClients} 登记；本拦截器覆盖单目标写出。
 */
@Order(NumberConstant.NUMBER_100)
public class QosRetryMessageInterceptor extends AbstractMessageInterceptor {

    @Override
    public boolean preHandle(Packet packet, Target target) {
        return true;
    }

    @Override
    public void postHandle(Packet packet, Target target) {
        QosRetryScheduler.schedule(packet, target);
    }
}
