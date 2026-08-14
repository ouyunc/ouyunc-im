package com.ouyunc.repository;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.base.model.GroupRequestSession;
import com.ouyunc.base.model.RequestSession;
import com.ouyunc.base.constant.enums.IdentityType;
import com.ouyunc.base.constant.enums.MessageDeliveryChannelEnum;
import com.ouyunc.repository.cs.CsImSessionRoute;
import com.ouyunc.repository.support.MessageIndexScope;
import com.ouyunc.domain.entity.FriendEntity;
import com.ouyunc.domain.entity.GroupEntity;
import com.ouyunc.domain.entity.GroupUserEntity;
import com.ouyunc.domain.entity.UserEntity;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.repository.support.RepositorySupports;
import io.netty.channel.ChannelHandlerContext;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 默认持久化仓库门面：对外 API 不变，实现委托至 {@link com.ouyunc.repository.support} 各模块。
 */
public enum DefaultRepository implements Repository {
    INSTANCE;

    @Override
    public CompletableFuture<?> save(Packet packet) {
        return RepositorySupports.MQ.save(packet);
    }

    public CompletableFuture<?> savePacket2Mq(String topic, String key, Packet packet) {
        return RepositorySupports.MQ.savePacket2Mq(topic, key, packet);
    }

    public void publishPacketAsync(String topic, String key, Packet packet, String failureContext) {
        RepositorySupports.MQ.publishPacketAsync(topic, key, packet, failureContext);
    }

    public void publishArchiveAsync(Packet packet) {
        RepositorySupports.MQ.publishArchiveAsync(packet);
    }

    public void publishJsonAsync(String topic, String key, String jsonBody, String failureContext) {
        RepositorySupports.MQ.publishJsonAsync(topic, key, jsonBody, failureContext);
    }

    @Override
    public boolean checkDup(Packet packet, String channelLoginIdentity) {
        return RepositorySupports.QOS.checkDup(packet, channelLoginIdentity);
    }

    public List<Packet> getPackets(String appKey, List<Long> packetIds) {
        return RepositorySupports.MESSAGE_PACKET_QUERY.getPackets(appKey, packetIds);
    }

    public Mono<List<Packet>> reactiveLoadWithdrawTargetPackets(Packet packet, String scopeId,
                                                                 MessageIndexScope scope, boolean isValidSender) {
        return RepositorySupports.WITHDRAW.reactiveLoadWithdrawTargetPackets(packet, scopeId, scope, isValidSender);
    }

    public Mono<Boolean> reactiveWithdrawMessage(Packet packet, String scopeId, MessageIndexScope scope,
                                                 List<Packet> targetPackets) {
        return RepositorySupports.WITHDRAW.reactiveWithdrawMessage(packet, scopeId, scope, targetPackets);
    }

    public Mono<Boolean> reactiveHandleOperation(ChannelHandlerContext ctx, Packet packet,
                                                 Mono<Boolean> validator,
                                                 Supplier<CompletableFuture<?>> mqSender,
                                                 Mono<Boolean> processor,
                                                 BiConsumer<ChannelHandlerContext, Packet> processorAfter,
                                                 Consumer<MessageEvent> exceptionConsumer,
                                                 ExceptionCodeEnum exceptionCode) {
        return RepositorySupports.REACTIVE_OPERATION.reactiveHandleOperation(ctx, packet, validator, mqSender,
                processor, processorAfter, exceptionConsumer, exceptionCode);
    }

    public <T> Mono<Boolean> reactiveHandleOperation(ChannelHandlerContext ctx, Packet packet,
                                                   Mono<T> preparer,
                                                   ExceptionCodeEnum verifyExceptionCode,
                                                   Supplier<CompletableFuture<?>> mqSender,
                                                   Function<T, Mono<Boolean>> processor,
                                                   BiConsumer<ChannelHandlerContext, Packet> processorAfter,
                                                   Consumer<MessageEvent> exceptionConsumer,
                                                   ExceptionCodeEnum processExceptionCode) {
        return RepositorySupports.REACTIVE_OPERATION.reactiveHandleOperation(ctx, packet, preparer, verifyExceptionCode,
                mqSender, processor, processorAfter, exceptionConsumer, processExceptionCode);
    }

    public Mono<List<Packet>> reactiveLoadValidatedReadReceiptPackets(Packet packet, String sessionId,
                                                                      IdentityType identityType,
                                                                      boolean isValidSender) {
        return RepositorySupports.READ_RECEIPT.reactiveLoadValidatedReadReceiptPackets(
                packet, sessionId, identityType, isValidSender);
    }

    public Mono<Boolean> reactiveReadReceiptMessage(Packet packet, IdentityType identityType, long expireTime,
                                                    List<Packet> targetPackets) {
        return RepositorySupports.READ_RECEIPT.reactiveReadReceiptMessage(
                packet, identityType, expireTime, targetPackets);
    }

    public Mono<Boolean> reactiveAdvanceSenderReadOffsetOnSend(Packet packet, IdentityType identityType, long expireTime) {
        return RepositorySupports.READ_RECEIPT.reactiveAdvanceSenderReadOffsetOnSend(packet, identityType, expireTime);
    }

    public Set<String> groupUsersIdentity(Packet packet) {
        return RepositorySupports.GROUP.groupUsersIdentity(packet);
    }

    public GroupUserEntity groupUserEntity(String appKey, String groupId, String memberId) {
        return RepositorySupports.GROUP.groupUserEntity(appKey, groupId, memberId);
    }

    public Mono<GroupUserEntity> groupUserEntityReactive(String appKey, String groupId, String memberId) {
        return RepositorySupports.GROUP.groupUserEntityReactive(appKey, groupId, memberId);
    }

    public Set<String> groupManagerAndLeaderUsersIdentity(Packet packet) {
        return RepositorySupports.GROUP.groupManagerAndLeaderUsersIdentity(packet);
    }

    public Map<String, Double> groupManagerAndLeaderUsersIdentityAndPost(Packet packet) {
        return RepositorySupports.GROUP.groupManagerAndLeaderUsersIdentityAndPost(packet);
    }

    public Mono<Boolean> reactiveSaveMessage(Packet packet, String sessionId, long expireTime) {
        return RepositorySupports.SESSION.reactiveSaveMessage(packet, sessionId, expireTime);
    }

    public Mono<Boolean> reactiveSaveOne2OneMessage(Packet packet, String sessionId, long expireTime) {
        return RepositorySupports.SESSION.reactiveSaveOne2OneMessage(packet, sessionId, expireTime,
                RepositorySupports.UNREAD);
    }

    public boolean saveJoinFriendRequestMessage(Packet packet, RequestSession requestSession, long expireTime) {
        return RepositorySupports.FRIEND.saveJoinFriendRequestMessage(packet, requestSession, expireTime);
    }

    public RequestSession getFriendRequestSession(String appKey, String from, String to) {
        return RepositorySupports.FRIEND.getFriendRequestSession(appKey, from, to);
    }

    public GroupRequestSession getGroupRequestSession(String appKey, String joiner, String groupId) {
        return RepositorySupports.GROUP.getGroupRequestSession(appKey, joiner, groupId);
    }

    public boolean saveRefuseFriendRequestMessage(Packet packet, RequestSession requestSession, long expireTime) {
        return RepositorySupports.FRIEND.saveRefuseFriendRequestMessage(packet, requestSession, expireTime);
    }

    public boolean isFriend(String appKey, String from, String to) {
        return RepositorySupports.FRIEND.isFriend(appKey, from, to);
    }

    public boolean inGroup(String appKey, String from, String groupId) {
        return RepositorySupports.GROUP.inGroup(appKey, from, groupId);
    }

    public Collection<String> getFriendIds(String appKey, String from) {
        return RepositorySupports.FRIEND.getFriendIds(appKey, from);
    }

    public Mono<FriendEntity> getFriendReactive(String appKey, String from, String to) {
        return RepositorySupports.FRIEND.getFriendReactive(appKey, from, to);
    }

    public GroupEntity getGroupEntity(String appKey, String groupId) {
        return RepositorySupports.GROUP.getGroupEntity(appKey, groupId);
    }

    public Mono<GroupEntity> getGroupEntityReactive(String appKey, String groupId) {
        return RepositorySupports.GROUP.getGroupEntityReactive(appKey, groupId);
    }

    public GroupEntity getGroupEntityFromDatabases(String appKey, String groupId) {
        return RepositorySupports.GROUP.getGroupEntityFromDatabases(appKey, groupId);
    }

    public Mono<GroupEntity> getGroupEntityFromDatabasesReactive(String appKey, String groupId) {
        return RepositorySupports.GROUP.getGroupEntityFromDatabasesReactive(appKey, groupId);
    }

    public UserEntity getUserEntity(String appKey, String identity) {
        return RepositorySupports.USER.getUserEntity(appKey, identity);
    }

    public boolean autoPassBindFriend(Packet packet, RequestSession requestSession, long expireTime) {
        return RepositorySupports.FRIEND.autoPassBindFriend(packet, requestSession, expireTime);
    }

    public boolean agreeBindFriend(String appKey, Packet packet, RequestSession requestSession, long expireTime) {
        return RepositorySupports.FRIEND.agreeBindFriend(appKey, packet, requestSession, expireTime);
    }

    public void releaseQosClaim(Packet packet) {
        RepositorySupports.QOS.releaseQosClaim(packet);
    }

    public boolean autoPassBindGroup(Packet packet, GroupRequestSession groupRequestSession, long expireTime) {
        return RepositorySupports.GROUP.autoPassBindGroup(packet, groupRequestSession, expireTime);
    }

    public boolean manualPassBindGroup(Packet packet, GroupRequestSession groupRequestSession, long expireTime) {
        return RepositorySupports.GROUP.manualPassBindGroup(packet, groupRequestSession, expireTime);
    }

    public boolean saveJoinGroupRequestMessage(Packet packet, GroupRequestSession groupRequestSession, long expireTime) {
        return RepositorySupports.GROUP.saveJoinGroupRequestMessage(packet, groupRequestSession, expireTime);
    }

    public boolean saveGroupRequestMessage(Packet packet, GroupRequestSession groupRequestSession, long expireTime) {
        return RepositorySupports.GROUP.saveGroupRequestMessage(packet, groupRequestSession, expireTime);
    }

    public boolean saveGroupRequestMessage(Packet packet, String groupId, String requestSessionId, long expireTime,
                                           Consumer<org.springframework.data.redis.connection.RedisConnection> consumer) {
        return RepositorySupports.GROUP.saveGroupRequestMessage(packet, groupId, requestSessionId, expireTime, consumer);
    }

    public void saveLastMessageForSession(String sessionId, Packet lastPacket, long expireTime, TimeUnit timeUnit) {
        RepositorySupports.SESSION.saveLastMessageForSession(sessionId, lastPacket, expireTime, timeUnit);
    }

    public void refreshSessionLastMessageAfterWithdraw(String appKey, String sessionId) {
        RepositorySupports.SESSION_LAST_MESSAGE.refreshAfterWithdraw(appKey, sessionId);
    }

    public void saveLastMessageForCsTicket(String ticketId, Packet lastPacket, long expireTime, TimeUnit timeUnit) {
        RepositorySupports.CS_TICKET_LAST_MESSAGE.save(ticketId, lastPacket, expireTime, timeUnit);
    }

    public Long getLastMessagePacketIdForCsTicket(String appKey, String ticketId) {
        return RepositorySupports.CS_TICKET_LAST_MESSAGE.getLastPacketId(appKey, ticketId);
    }

    public void deleteCsTicketLastMessage(String appKey, String ticketId) {
        RepositorySupports.CS_TICKET_LAST_MESSAGE.delete(appKey, ticketId);
    }

    public Mono<Boolean> reactiveSaveCsTicketMessage(Packet packet, CsImSessionRoute route, long expireTime) {
        return RepositorySupports.CS_TICKET_MESSAGE.reactiveSaveCsTicketMessage(packet, route, expireTime);
    }

    public Mono<List<Packet>> reactiveLoadValidatedCsReadReceiptPackets(Packet packet, CsImSessionRoute route,
                                                                        byte deviceType) {
        return RepositorySupports.CS_TICKET_READ_RECEIPT.reactiveLoadValidatedCsReadReceiptPackets(
                packet, route, deviceType);
    }

    public Mono<Boolean> reactiveCsReadReceiptMessage(Packet packet, CsImSessionRoute route, byte deviceType,
                                                      long expireTime, List<Packet> targetPackets) {
        return RepositorySupports.CS_TICKET_READ_RECEIPT.reactiveCsReadReceiptMessage(
                packet, route, deviceType, expireTime, targetPackets);
    }

    public Mono<Boolean> reactiveAdvanceCsSenderReadOffsetOnSend(Packet packet, CsImSessionRoute route, byte deviceType,
                                                                 long expireTime) {
        return RepositorySupports.CS_TICKET_READ_RECEIPT.reactiveAdvanceCsSenderReadOffsetOnSend(
                packet, route, deviceType, expireTime);
    }

    public void refreshCsTicketLastMessageAfterWithdraw(String appKey, String ticketId) {
        RepositorySupports.CS_TICKET_LAST_MESSAGE.refreshAfterWithdraw(appKey, ticketId);
    }

    public MessageDeliveryChannelEnum resolveFriendDeliveryChannel(String appKey, String ownerUserId, String peerUserId) {
        return RepositorySupports.DELIVERY_CHANNEL.resolveFriendDeliveryChannel(appKey, ownerUserId, peerUserId);
    }

    public MessageDeliveryChannelEnum resolveGroupMemberDeliveryChannel(String appKey, String groupId, String memberId) {
        return RepositorySupports.DELIVERY_CHANNEL.resolveGroupMemberDeliveryChannel(appKey, groupId, memberId);
    }

    public void publishExternalChannelOutbound(Packet packet, String recipientId, MessageDeliveryChannelEnum channel) {
        RepositorySupports.DELIVERY_CHANNEL.publishExternalOutbound(packet, recipientId, channel);
    }

    /** 读取 CS 写入的客服会话路由（主键 ticketId = 消息 correlationId）。 */
    public CsImSessionRoute getCsImSessionRoute(String appKey, String ticketId) {
        return RepositorySupports.CS_IM_SESSION_ROUTE.getRoute(appKey, ticketId);
    }

    /**
     * 投递前用 Redis 最新 assignee/epoch 覆盖快照；路由已删返回 null。
     */
    public CsImSessionRoute mergeCsImSessionRouteDelivery(String appKey, CsImSessionRoute snapshot) {
        return RepositorySupports.CS_IM_SESSION_ROUTE.mergeLiveDelivery(appKey, snapshot);
    }
}
