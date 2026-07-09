package com.ouyunc.message.processor.http.push.delivery;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.GroupMessagePushModeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.model.ClientInfo;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.constant.enums.IdentityType;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.AtMentionHelper;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageDeliveryRouteHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.helper.MessageRefHelper;
import com.ouyunc.message.processor.http.push.IngressPacketHelper;
import com.ouyunc.repository.DefaultRepository;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 推送：群聊投递。
 */
public enum GroupHttpPushDeliveryStrategy implements HttpProcessor {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(GroupHttpPushDeliveryStrategy.class);

    @Override
    public MessageTypeEnum messageType() {
        return MessageTypeEnum.GROUP;
    }

    @Override
    public void process(Packet packet) {
        Set<String> groupUserIdentitySet = DefaultRepository.INSTANCE.groupUsersIdentity(packet);
        if (CollectionUtils.isEmpty(groupUserIdentitySet)) {
            log.error("HTTP 推送群组 {} 不存在群成员, packet={}", packet.getMessage().getTo(), packet);
            HttpPushDeliverySupport.publishException(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群成员", packet);
            return;
        }
        boolean skipSenderMembership = IngressPacketHelper.isHttpPush(packet)
                && IngressPacketHelper.isSystemLikeSender(packet.getMessage());
        if (!skipSenderMembership && !groupUserIdentitySet.remove(packet.getMessage().getFrom())) {
            log.error("HTTP 推送发送方 {} 不在群 {} 中", packet.getMessage().getFrom(), packet.getMessage().getTo());
            HttpPushDeliverySupport.publishException(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "发送者不在群组中", packet);
            return;
        }
        Set<String> allGroupMembers = new HashSet<>(groupUserIdentitySet);
        allGroupMembers.add(packet.getMessage().getFrom());
        if (!normalizeGroupAtOrReject(packet, allGroupMembers)) {
            return;
        }
        if (!MessageRefHelper.normalizeMessageRefOrReject(packet)) {
            return;
        }
        DefaultRepository.INSTANCE.reactiveSaveMessage(packet, packet.getMessage().getTo(),
                        MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)
                .subscribe(
                        saved -> {
                            if (!Boolean.TRUE.equals(saved)) {
                                log.error("HTTP 推送群聊落库失败: {}", packet);
                                HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                                        "群聊消息写入会话失败", packet);
                                return;
                            }
                            DefaultRepository.INSTANCE.saveLastMessageForSession(packet.getMessage().getTo(), packet,
                                    MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
                            DefaultRepository.INSTANCE.reactiveAdvanceSenderReadOffsetOnSend(packet, IdentityType.GROUP,
                                            MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP)
                                    .subscribe(ignored -> { }, e -> log.warn(
                                            "HTTP 推送更新群聊已读 offset 失败, packetId={}", packet.getPacketId(), e));
                            pushGroupOnline(packet, groupUserIdentitySet);
                        },
                        error -> {
                            log.error("HTTP 推送群聊落库异常, packetId={}", packet.getPacketId(), error);
                            HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                                    "群聊持久化异常: " + error.getMessage(), packet);
                        });
    }

    private static void pushGroupOnline(Packet packet, Set<String> groupMembers) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        ClientInfo clientInfo = MessageServerContext.localClientInfo(appKey, message.getFrom());
        if (clientInfo != null && clientInfo.getSelfSync()) {
            List<LoginClientInfo> fromClients = ClientHelper.onlineAll(appKey, message.getFrom(),
                    MessageServerContext.deviceType(appKey, packet.getDeviceType()));
            if (CollectionUtils.isNotEmpty(fromClients)) {
                MessageHelper.asyncSendMessage(packet, fromClients);
            }
        }
        GroupMessagePushModeEnum mode = MessageServerContext.serverProperties().getGroupMessagePushMode();
        if (GroupMessagePushModeEnum.PUSH.equals(mode)) {
            deliverToAllGroupMembers(packet, groupMembers);
        } else if (GroupMessagePushModeEnum.PULL.equals(mode)) {
            List<String> atList = message.getAt();
            if (CollectionUtils.isNotEmpty(atList)) {
                deliverToAtMembers(packet, atList, groupMembers);
            }
        } else if (GroupMessagePushModeEnum.PULL_PUSH.equals(mode)) {
            if (groupMembers.size() + NumberConstant.NUMBER_1
                    > MessageServerContext.serverProperties().getGroupMessageThreshold()) {
                List<String> atList = message.getAt();
                if (CollectionUtils.isNotEmpty(atList)) {
                    deliverToAtMembers(packet, atList, groupMembers);
                }
            } else {
                deliverToAllGroupMembers(packet, groupMembers);
            }
        } else {
            log.warn("HTTP 推送暂不支持群消息推送模式: {}", mode);
        }
    }

    private static void deliverToAllGroupMembers(Packet packet, Set<String> groupMembers) {
        MessageDeliveryRouteHelper.deliverGroupMembers(packet, groupMembers);
    }

    private static void deliverToAtMembers(Packet packet, List<String> atList, Set<String> groupMembers) {
        Set<String> targets = AtMentionHelper.resolveDeliveryTargets(atList, groupMembers);
        Message message = packet.getMessage();
        String groupId = message.getTo();
        targets.forEach(member -> MessageDeliveryRouteHelper.deliverGroupMember(packet, groupId, member));
    }

    private static boolean normalizeGroupAtOrReject(Packet packet, Set<String> allGroupMembers) {
        Message message = packet.getMessage();
        List<String> at = message.getAt();
        if (CollectionUtils.isEmpty(at)) {
            return true;
        }
        try {
            message.setAt(AtMentionHelper.normalizeAndValidate(at, allGroupMembers));
            return true;
        } catch (IllegalArgumentException ex) {
            log.warn("HTTP 推送群@校验失败: {} | packet={}", ex.getMessage(), packet);
            HttpPushDeliverySupport.publishException(ExceptionCodeEnum.GROUP_AT_MENTION_INVALID_ERROR, ex.getMessage(), packet);
            return false;
        }
    }
}
