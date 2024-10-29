package com.ouyunc.message.processor;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MqttMessageContentTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.MqttTopicSubscriptionOption;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.MqttCodecUtil;
import com.ouyunc.repository.MqttRepository;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * mqtt 订阅
 */
public class MqttSubscribeMessageContentProcessor extends AbstractBaseProcessor<Integer>{
    private static final Logger log = LoggerFactory.getLogger(MqttSubscribeMessageContentProcessor.class);

    @Override
    public MessageContentType type() {
        return MqttMessageContentTypeEnum.MQTT_SUBSCRIBE;
    }
    @SuppressWarnings("unchecked")
    @Override
    public MqttRepository repository() {
        return new MqttRepository();
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("MqttSubscribeMessageContentProcessor 正在处理外部客户端订阅 {} ...", packet);
        }
        Message message = packet.getMessage();
        MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(packet.getRetain());
        MqttMessage mqttMessage = MqttCodecUtil.decode(mqttVersion, message.getContent());
        if (mqttMessage instanceof MqttSubscribeMessage mqttSubscribeMessage) {
            List<MqttTopicSubscription> topicSubscriptions = mqttSubscribeMessage.payload().topicSubscriptions();
            if (this.validTopicFilter(topicSubscriptions)) {
                LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
                if (loginClientInfo == null) {
                    log.error("MqttSubscribeMessageContentProcessor 登录信息不存在！正在关闭该channel");
                    ctx.close();
                    return;
                }
                String comboIdentity = IdentityUtil.generalComboIdentity(loginClientInfo.getAppKey(), loginClientInfo.getIdentity(), loginClientInfo.getDeviceType());
                List<Integer> mqttQoSList = new ArrayList<Integer>();
                List<MqttTopicSubscriptionOption> topicSubscriptionOptionList = new ArrayList<>();
                topicSubscriptions.forEach(topicSubscription -> {
                    String topicFilter = topicSubscription.topicFilter();
                    MqttQoS mqttQoS = topicSubscription.qualityOfService();
                    MqttSubscriptionOption mqttSubscriptionOption = topicSubscription.option();
                    MqttTopicSubscriptionOption mqttTopicSubscriptionOption = new MqttTopicSubscriptionOption(topicFilter, mqttQoS, mqttSubscriptionOption.isNoLocal(), mqttSubscriptionOption.isRetainAsPublished(), mqttSubscriptionOption.retainHandling());
                    topicSubscriptionOptionList.add(mqttTopicSubscriptionOption);
                    // 判断是否存在该主题，如果存在则直接添加关系，，如果不存在判断权限是否允许创建主题并订阅，如果有权限则创建主题并添加关系，否则返回信息
                    mqttQoSList.add(mqttQoS.value());
                });
                // 批量订阅
                repository().subscribe(loginClientInfo.getAppKey(), comboIdentity, topicSubscriptionOptionList);
                MqttSubAckMessage subAckMessage = (MqttSubAckMessage) MqttMessageFactory.newMessage(
                        new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                        MqttMessageIdVariableHeader.from(mqttSubscribeMessage.variableHeader().messageId()),
                        new MqttSubAckPayload(mqttQoSList));
                ctx.writeAndFlush(subAckMessage);
                // 发布保留消息
                topicSubscriptions.forEach(topicSubscription -> {
                    String topicFilter = topicSubscription.topicFilter();
                    MqttQoS mqttQoS = topicSubscription.qualityOfService();
                    // @todo
                    //this.sendRetainMessage(ctx, topicFilter, mqttQoS);
                });
            } else {
                log.error("MqttSubscribeMessageContentProcessor 订阅主题非法！");
                // 非法topicFilter按订阅失败处理
                MqttSubAckMessage subAckMessage = (MqttSubAckMessage) MqttMessageFactory.newMessage(
                        new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                        MqttMessageIdVariableHeader.from(mqttSubscribeMessage.variableHeader().messageId()),
                        new MqttSubAckPayload(MqttReasonCodes.SubAck.TOPIC_FILTER_INVALID));
                ctx.writeAndFlush(subAckMessage);
                ctx.close();
            }
        }else {
            log.error("MqttSubscribeMessageContentProcessor 订阅消息解析失败！");
        }
    }

    private boolean validTopicFilter(List<MqttTopicSubscription> topicSubscriptions) {
        for (MqttTopicSubscription topicSubscription : topicSubscriptions) {
            String topicFilter = topicSubscription.topicFilter();
            // 以#或+符号开头的、以/符号结尾的及不存在/符号的订阅按非法订阅处理, 这里没有参考标准协议
            if (StringUtils.startsWith(topicFilter, "#") || StringUtils.startsWith(topicFilter, "+") || StringUtils.endsWith(topicFilter, "/") || !StringUtils.contains(topicFilter, '/')) return false;
            if (StringUtils.contains(topicFilter, '#')) {
                // 不是以/#字符串结尾的订阅按非法订阅处理
                if (!StringUtils.endsWith(topicFilter, "/#")) return false;
                // 如果出现多个#符号的订阅按非法订阅处理
                if (StringUtils.countMatches(topicFilter, '#') > 1) return false;
            }
            if (StringUtils.contains(topicFilter, '+')) {
                //如果+符号和/+字符串出现的次数不等的情况按非法订阅处理
                if (StringUtils.countMatches(topicFilter, '+') != StringUtils.countMatches(topicFilter, "/+")) return false;
            }
        }
        return true;
    }


//    private void sendRetainMessage(ChannelHandlerContext ctx, String topicFilter, MqttQoS mqttQoS) {
//        List<RetainMessageStore> retainMessageStores = retainMessageStoreService.search(topicFilter);
//        retainMessageStores.forEach(retainMessageStore -> {
//            MqttQoS respQoS = retainMessageStore.getMqttQoS() > mqttQoS.value() ? mqttQoS : MqttQoS.valueOf(retainMessageStore.getMqttQoS());
//            if (respQoS == MqttQoS.AT_MOST_ONCE) {
//                MqttPublishMessage publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
//                        new MqttFixedHeader(MqttMessageType.PUBLISH, false, respQoS, false, 0),
//                        new MqttPublishVariableHeader(retainMessageStore.getTopic(), 0), Unpooled.buffer().writeBytes(retainMessageStore.getMessageBytes()));
//                ctx.writeAndFlush(publishMessage);
//            }
//            if (respQoS == MqttQoS.AT_LEAST_ONCE) {
//                int messageId = messageIdService.getNextMessageId();
//                MqttPublishMessage publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
//                        new MqttFixedHeader(MqttMessageType.PUBLISH, false, respQoS, false, 0),
//                        new MqttPublishVariableHeader(retainMessageStore.getTopic(), messageId), Unpooled.buffer().writeBytes(retainMessageStore.getMessageBytes()));
//                ctx.writeAndFlush(publishMessage);
//            }
//            if (respQoS == MqttQoS.EXACTLY_ONCE) {
//                int messageId = messageIdService.getNextMessageId();
//                MqttPublishMessage publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
//                        new MqttFixedHeader(MqttMessageType.PUBLISH, false, respQoS, false, 0),
//                        new MqttPublishVariableHeader(retainMessageStore.getTopic(), messageId), Unpooled.buffer().writeBytes(retainMessageStore.getMessageBytes()));
//                ctx.writeAndFlush(publishMessage);
//            }
//        });
//    }
}
