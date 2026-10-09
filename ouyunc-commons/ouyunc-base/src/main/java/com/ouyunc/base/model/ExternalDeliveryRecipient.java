package com.ouyunc.base.model;

import com.ouyunc.base.constant.enums.MessageDeliveryChannelEnum;

/** 外渠投递状态的收件人身份；列表顺序与同批 broker 确认结果一致。 */
public record ExternalDeliveryRecipient(String recipientId, MessageDeliveryChannelEnum channel) {
}
