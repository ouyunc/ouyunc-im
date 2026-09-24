package com.ouyunc.message.helper;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.model.*;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.GroupRequestContent;
import com.ouyunc.domain.entity.GroupEntity;
import com.ouyunc.domain.entity.GroupUserEntity;
import com.ouyunc.domain.entity.UserEntity;
import com.ouyunc.repository.DefaultRepository;
import org.apache.commons.lang3.StringUtils;

/** 在领域消息写入 Kafka 前生成完整、可重放的申请状态快照。 */
public final class RequestEventContextFactory {

    private RequestEventContextFactory() {
    }

    static void ensure(Packet packet) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        if (metadata.getRequestEventContext() != null) {
            return;
        }
        RequestEventContext context = create(packet.getMessageType(), message);
        if (context == null || StringUtils.isBlank(context.getRequestSessionId())) {
            throw new IllegalStateException("申请事件缺少可重放的请求会话快照, messageId=" + message.getId());
        }
        metadata.setRequestEventContext(context);
    }

    /** 用刚刚写入 Redis 的会话覆盖快照，避免 ensure 按旧进度重新推导。 */
    public static void capture(Packet packet, RequestSession session) {
        packet.getMessage().getMetadata().setRequestEventContext(friendContext(session));
    }

    /** 用刚刚写入 Redis 的群会话覆盖快照。 */
    public static void capture(Packet packet, GroupRequestSession session) {
        packet.getMessage().getMetadata().setRequestEventContext(groupContext(session));
    }

    /** 使用协议枚举识别申请消息，避免协议值调整后事件快照逻辑静默失效。 */
    private static RequestEventContext create(byte messageType, Message message) {
        if (MessageTypeEnum.ONE_2_ONE_FRIEND_REQUEST_JOIN.getType().equals(messageType)) {
            return friendJoin(message);
        }
        if (MessageTypeEnum.ONE_2_ONE_FRIEND_REQUEST_AGREE.getType().equals(messageType)
                || MessageTypeEnum.ONE_2_ONE_FRIEND_REQUEST_REFUSE.getType().equals(messageType)) {
            return friendApproval(message);
        }
        if (MessageTypeEnum.GROUP_REQUEST_JOIN.getType().equals(messageType)) {
            return activeGroupJoin(message);
        }
        if (MessageTypeEnum.GROUP_REQUEST_INVITE_JOIN.getType().equals(messageType)) {
            return invitedGroupJoin(message);
        }
        if (MessageTypeEnum.GROUP_REQUEST_AGREE.getType().equals(messageType)
                || MessageTypeEnum.GROUP_REQUEST_REFUSE.getType().equals(messageType)) {
            return groupManagerApproval(message);
        }
        if (MessageTypeEnum.GROUP_REQUEST_INVITED_JOINER_AGREE.getType().equals(messageType)) {
            return invitedJoinerApproval(message, true);
        }
        if (MessageTypeEnum.GROUP_REQUEST_INVITED_JOINER_REFUSE.getType().equals(messageType)) {
            return invitedJoinerApproval(message, false);
        }
        return null;
    }

    private static RequestEventContext friendJoin(Message message) {
        DefaultRepository repository = DefaultRepository.INSTANCE;
        String appKey = message.getMetadata().getAppKey();
        RequestSession existing = repository.getFriendRequestSession(appKey, message.getFrom(), message.getTo());
        RequestSession session = existing;
        if (session == null || session.getProgress() > RequestSessionProgress.JOINING.value()) {
            UserEntity recipient = repository.getUserEntity(appKey, message.getTo());
            if (recipient == null) {
                return null;
            }
            int progress = FriendJoinPolicy.AUTO_PASS.value().equals(recipient.getFriendJoinPolicy())
                    ? RequestSessionProgress.AGREEING.value() : RequestSessionProgress.JOINING.value();
            session = new RequestSession(message.getId(), progress);
        }
        return friendContext(session);
    }

    private static RequestEventContext friendApproval(Message message) {
        RequestSession session = DefaultRepository.INSTANCE.getFriendRequestSession(
                message.getMetadata().getAppKey(), message.getTo(), message.getFrom());
        return session == null ? null : friendContext(session);
    }

    private static RequestEventContext activeGroupJoin(Message message) {
        DefaultRepository repository = DefaultRepository.INSTANCE;
        String appKey = message.getMetadata().getAppKey();
        GroupRequestSession existing = repository.getGroupRequestSession(appKey, message.getFrom(), message.getTo());
        if (existing != null && existing.getProgress() <= RequestSessionProgress.JOINING.value()
                && GroupRequestSessionWay.ACTIVE.value().equals(existing.getWay())) {
            return groupContext(existing);
        }
        GroupEntity group = repository.getGroupEntity(appKey, message.getTo());
        if (group == null) {
            return null;
        }
        int progress = GroupJoinPolicy.AUTO_PASS.value().equals(group.getGroupJoinPolicy())
                ? RequestSessionProgress.AGREEING.value() : RequestSessionProgress.JOINING.value();
        GroupRequestSession session = GroupRequestSession.newGroupBuilder()
                .sessionId(message.getId()).progress(progress).joiner(message.getFrom())
                .groupId(message.getTo()).channel(GroupRequestSessionChannel.OTHER.value())
                .way(GroupRequestSessionWay.ACTIVE.value())
                .joinerProcessStatus(GroupJoinerProcessStatus.AGREE.value()).build();
        return groupContext(session);
    }

    private static RequestEventContext invitedGroupJoin(Message message) {
        GroupRequestContent content = JSON.parseObject(message.getContent(), GroupRequestContent.class);
        if (content == null || StringUtils.isBlank(content.getIdentity())) {
            return null;
        }
        DefaultRepository repository = DefaultRepository.INSTANCE;
        String appKey = message.getMetadata().getAppKey();
        GroupRequestSession existing = repository.getGroupRequestSession(appKey, content.getIdentity(), message.getTo());
        if (existing != null && existing.getProgress() <= RequestSessionProgress.JOINING.value()
                && GroupRequestSessionWay.INVITED.value().equals(existing.getWay())) {
            return groupContext(existing);
        }
        GroupEntity group = repository.getGroupEntity(appKey, message.getTo());
        UserEntity joiner = repository.getUserEntity(appKey, content.getIdentity());
        GroupUserEntity inviter = repository.groupUserEntity(appKey, message.getTo(), message.getFrom());
        if (group == null || joiner == null || inviter == null) {
            return null;
        }
        boolean inviterCanApprove = GroupUserPost.LEADER.value().equals(inviter.getPost())
                || GroupUserPost.MANAGER.value().equals(inviter.getPost());
        boolean joinerAutoAccept = GroupInvitePolicy.AUTO_PASS.value().equals(joiner.getGroupInvitePolicy());
        boolean skipAdminReview = inviterCanApprove
                || GroupJoinPolicy.AUTO_PASS.value().equals(group.getGroupJoinPolicy());
        int progress = joinerAutoAccept && skipAdminReview
                ? RequestSessionProgress.AGREEING.value() : RequestSessionProgress.JOINING.value();
        GroupRequestSession session = GroupRequestSession.newGroupBuilder()
                .sessionId(message.getId()).progress(progress).inviter(message.getFrom())
                .inviterPost(inviter.getPost()).joiner(content.getIdentity()).groupId(message.getTo())
                .joinerProcessStatus(joinerAutoAccept ? GroupJoinerProcessStatus.AGREE.value()
                        : GroupJoinerProcessStatus.PENDING.value())
                .way(GroupRequestSessionWay.INVITED.value()).channel(GroupRequestSessionChannel.OTHER.value())
                .processor(inviterCanApprove ? message.getFrom() : null)
                .processorPost(inviterCanApprove ? inviter.getPost() : null).build();
        return groupContext(session);
    }

    private static RequestEventContext groupManagerApproval(Message message) {
        GroupRequestContent content = JSON.parseObject(message.getContent(), GroupRequestContent.class);
        if (content == null || StringUtils.isBlank(content.getIdentity())) {
            return null;
        }
        GroupRequestSession session = DefaultRepository.INSTANCE.getGroupRequestSession(
                message.getMetadata().getAppKey(), content.getIdentity(), message.getTo());
        return session == null ? null : groupContext(session);
    }

    private static RequestEventContext invitedJoinerApproval(Message message, boolean agree) {
        DefaultRepository repository = DefaultRepository.INSTANCE;
        String appKey = message.getMetadata().getAppKey();
        GroupRequestSession session = repository.getGroupRequestSession(appKey, message.getFrom(), message.getTo());
        if (session == null) {
            return null;
        }
        RequestEventContext context = groupContext(session);
        if (!agree) {
            context.setJoinerProcessStatus(GroupJoinerProcessStatus.REFUSE.value());
            return context;
        }
        context.setJoinerProcessStatus(GroupJoinerProcessStatus.AGREE.value());
        GroupEntity group = repository.getGroupEntity(appKey, message.getTo());
        GroupUserEntity inviter = StringUtils.isBlank(session.getInviter()) ? null
                : repository.groupUserEntity(appKey, message.getTo(), session.getInviter());
        boolean inviterCanApprove = inviter != null && (GroupUserPost.LEADER.value().equals(inviter.getPost())
                || GroupUserPost.MANAGER.value().equals(inviter.getPost()));
        boolean skipAdminReview = inviterCanApprove
                || group != null && GroupJoinPolicy.AUTO_PASS.value().equals(group.getGroupJoinPolicy());
        if (skipAdminReview) {
            context.setProgress(RequestSessionProgress.AGREEING.value());
            if (inviterCanApprove) {
                context.setProcessor(session.getInviter());
                context.setProcessorPost(inviter.getPost());
            }
        }
        return context;
    }

    private static RequestEventContext friendContext(RequestSession session) {
        RequestEventContext context = new RequestEventContext();
        context.setRequestSessionId(session.getSessionId());
        context.setProgress(session.getProgress());
        return context;
    }

    private static RequestEventContext groupContext(GroupRequestSession session) {
        RequestEventContext context = new RequestEventContext();
        context.setRequestSessionId(session.getSessionId());
        context.setProgress(session.getProgress());
        context.setInviter(session.getInviter());
        context.setInviterPost(session.getInviterPost());
        context.setJoiner(session.getJoiner());
        context.setJoinerProcessStatus(session.getJoinerProcessStatus());
        context.setGroupId(session.getGroupId());
        context.setProcessor(session.getProcessor());
        context.setProcessorPost(session.getProcessorPost());
        context.setWay(session.getWay());
        context.setChannel(session.getChannel());
        return context;
    }
}
