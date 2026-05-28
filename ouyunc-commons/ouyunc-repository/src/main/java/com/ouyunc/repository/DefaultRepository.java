package com.ouyunc.repository;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.domain.base.GroupRequestSession;
import com.ouyunc.domain.base.RequestSession;
import com.ouyunc.domain.constants.IdentityType;
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

    @Override
    public boolean checkDup(Packet packet, String channelLoginIdentity) {
        return RepositorySupports.QOS.checkDup(packet, channelLoginIdentity);
    }

    public List<Packet> getPackets(String appKey, List<Long> packetIds) {
        return RepositorySupports.MESSAGE_PACKET_QUERY.getPackets(appKey, packetIds);
    }

    public Mono<List<Packet>> reactiveLoadWithdrawTargetPackets(Packet packet, String sessionId, boolean isValidSender) {
        return RepositorySupports.WITHDRAW.reactiveLoadWithdrawTargetPackets(packet, sessionId, isValidSender);
    }

    public Mono<Boolean> reactiveWithdrawMessage(Packet packet, String sessionId, List<Packet> targetPackets) {
        return RepositorySupports.WITHDRAW.reactiveWithdrawMessage(packet, sessionId, targetPackets);
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

    public Mono<Boolean> reactiveValidReadReceiptMessage(Packet packet, String sessionId, IdentityType identityType,
                                                         boolean isValidSender) {
        return RepositorySupports.READ_RECEIPT.reactiveValidReadReceiptMessage(packet, sessionId, identityType, isValidSender);
    }

    public Mono<Boolean> reactiveReadReceiptMessage(Packet packet, IdentityType identityType, long expireTime) {
        return RepositorySupports.READ_RECEIPT.reactiveReadReceiptMessage(packet, identityType, expireTime);
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
}
