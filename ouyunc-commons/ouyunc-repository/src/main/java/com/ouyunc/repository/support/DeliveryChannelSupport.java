package com.ouyunc.repository.support;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.MessageDeliveryChannelEnum;
import com.ouyunc.base.model.ExternalChannelOutboundPayload;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.domain.entity.FriendEntity;
import com.ouyunc.domain.entity.GroupUserEntity;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 按好友/群成员 {@code channel} 解析投递渠道，并将外渠下行发布到 Kafka。
 */
public final class DeliveryChannelSupport {

    private static final Logger log = LoggerFactory.getLogger(DeliveryChannelSupport.class);

    private final FriendRepositorySupport friendSupport;
    private final GroupMembershipSupport groupSupport;
    private final MessageMqPublisherSupport mqSupport;

    public DeliveryChannelSupport(FriendRepositorySupport friendSupport,
                                  GroupMembershipSupport groupSupport,
                                  MessageMqPublisherSupport mqSupport) {
        this.friendSupport = friendSupport;
        this.groupSupport = groupSupport;
        this.mqSupport = mqSupport;
    }

    /**
     * 发送方视角：对端好友（{@code friendUserId=peerUserId}）的投递渠道。
     * <p>
     * 客服场景通常只维护坐席→客户的好友记录（channel=WHATSAPP）；客户上行推坐席时
     * 无反向记录则默认 IM。
     */
    public MessageDeliveryChannelEnum resolveFriendDeliveryChannel(String appKey, String ownerUserId, String peerUserId) {
        FriendEntity friend = friendSupport.getFriendEntity(appKey, ownerUserId, peerUserId);
        if (friend == null || friend.getChannel() == null) {
            return MessageDeliveryChannelEnum.IM;
        }
        return MessageDeliveryChannelEnum.fromCode(friend.getChannel());
    }

    public MessageDeliveryChannelEnum resolveGroupMemberDeliveryChannel(String appKey, String groupId, String memberId) {
        GroupUserEntity member = groupSupport.groupUserEntity(appKey, groupId, memberId);
        if (member == null || member.getChannel() == null) {
            return MessageDeliveryChannelEnum.IM;
        }
        return MessageDeliveryChannelEnum.fromCode(member.getChannel());
    }

    public void publishExternalOutbound(Packet packet, String recipientId, MessageDeliveryChannelEnum channel) {
        if (packet == null || packet.getMessage() == null || channel == null || channel.isIm()) {
            return;
        }
        Message message = packet.getMessage();
        ExternalChannelOutboundPayload payload = new ExternalChannelOutboundPayload();
        payload.setAppKey(message.getMetadata().getAppKey());
        payload.setMessageId(message.getId());
        payload.setPacketId(packet.getPacketId());
        payload.setMessageType(packet.getMessageType());
        payload.setFrom(message.getFrom());
        payload.setTo(recipientId);
        payload.setDeliveryChannel(channel.getCode());
        payload.setContentType(message.getContentType());
        payload.setContent(message.getContent());
        payload.setExtra(message.getExtra());
        payload.setCreateTime(message.getCreateTime());

        String partitionKey = StringUtils.defaultIfBlank(message.getFrom(), recipientId);
        mqSupport.publishJsonAsync(MqConstant.MQ_EXTERNAL_CHANNEL_OUTBOUND_TOPIC, partitionKey,
                JSON.toJSONString(payload), "外部渠道下行");
        log.debug("已发布外部渠道下行, to={}, channel={}, packetId={}", recipientId, channel.getKey(), packet.getPacketId());
    }

}
