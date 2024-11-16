package com.ouyunc.message.listener;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.model.MqttLoginClientInfo;
import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.ClientLogoutEvent;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.mqtt.*;
import io.netty.util.CharsetUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 离线监听器
 **/
public class ClientLogoutListener implements MessageListener<ClientLogoutEvent> {
    private static final Logger log = LoggerFactory.getLogger(ClientLogoutListener.class);


    /**
     * @Author fzx
     * @Description 处理客户端离线事件，比如发送离线预警到mq
     */
    @Override
    public void onApplicationEvent(ClientLogoutEvent event) {
        if (log.isDebugEnabled()) {
            log.debug("离线事件监听器正在处理：{}", event);
        }
        Object source = event.getSource();
        if (source == null) {
            return;
        }
        // 处理mqtt遗嘱信息
        if (source instanceof MqttLoginClientInfo mqttLoginClientInfo && mqttLoginClientInfo.getEnableWill() == MessageConstant.ONE && StringUtils.isNoneBlank(mqttLoginClientInfo.getWillMessage())) {
            MqttMessage willMqttMessage = MqttMessageFactory.newMessage(
                    new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.valueOf(mqttLoginClientInfo.getQos()), mqttLoginClientInfo.getIsWillRetain() == MessageConstant.ONE, 0),
                    new MqttPublishVariableHeader(mqttLoginClientInfo.getWillTopic(), 0), ByteBufAllocator.DEFAULT.buffer().writeBytes(mqttLoginClientInfo.getWillMessage().getBytes(CharsetUtil.UTF_8)));
            // @todo 发送遗嘱消息给订阅willTopic 的客户端

        }

        // do nothing
    }
}
