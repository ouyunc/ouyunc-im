package com.ouyunc.message.processor.http.push.delivery;

import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.processor.Processor;

/**
 * HTTP 推送按消息类型的投递策略。
 */
public interface HttpProcessor extends Processor<Packet> {

    /**
     * 消息类型
     * @return
     */
   MessageTypeEnum messageType();

}
