package com.ouyunc.message.processor;

import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.helper.SenderReadCompletionSupport;

import com.ouyunc.core.exception.ExceptionReporter;
import com.ouyunc.base.exception.DeliveryRunBusyException;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.model.ClientInfo;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.constant.enums.IdentityType;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.CommittedDelivery;
import com.ouyunc.message.helper.MessageContentNormalizer;
import com.ouyunc.message.helper.MessageAcceptPipelineHelper;
import com.ouyunc.message.helper.QosCommittedDeliverySupport;
import com.ouyunc.message.helper.QosRequestFingerprintSupport;
import com.ouyunc.message.helper.MessageSubmissionResponseHelper;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageDeliveryPlanner;
import com.ouyunc.message.processor.http.push.IngressPacketHelper;
import com.ouyunc.message.schedule.ScheduleTimer;
import com.ouyunc.message.validator.*;
import com.ouyunc.repository.support.GroupMembershipSupport;
import com.ouyunc.base.constant.enums.MessageIndexScopeEnum;
import com.ouyunc.base.constant.enums.SaveMessageOutcomeEnum;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;


/**
 * 群聊消息处理器。
 * <p>标准管线：身份权限 → 成员/@ /ref 规范化 → 内容安全 → MQ 归档 → Redis 热写 → 仅成功/重复时 ACK → 新写入才扇出。
 * 已读/撤回在对应操作成功后再 ACK（不走 SAVE）。</p>
 */
public final class GroupMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(GroupMessageBiProcessor.class);
    /** 同一持久化消息只保留一个内存补投链，避免 Redis 故障时重复占用堆。 */
    private static final ConcurrentHashMap<String, Boolean> PENDING_FANOUT_RECOVERY = new ConcurrentHashMap<>();


    @Override
    public MessageType type() {
        return MessageTypeEnum.GROUP;
    }

    @Override
    public Mono<Boolean> preProcess(ChannelHandlerContext ctx, Packet packet) {
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过", "GroupMessageBiProcessor.process", packet);
            ctx.close();
            return Mono.just(false);
        }
        // 群 @、引用和内容安全都会改写消息，判重必须保留可信身份下的原始请求指纹。
        QosRequestFingerprintSupport.captureIfAbsent(packet);
        // QoS 判重占位；正式归档挪到 process（规范化 + 内容安全之后），避免 REJECT/MASK 冷热不一致
        if (qosPreHandle(ctx, packet)) {
            return Mono.just(false);
        }
        return MessageAcceptPipelineHelper.gateWhenPassed(ctx, packet,
                PermissionValidator.INSTANCE.negate()
                        .or(FromToValidator.INSTANCE)
                        .or(BlackListValidator.INSTANCE)
                        .or(GroupSilenceValidator.INSTANCE)
                        .or(GroupUserValidator.INSTANCE.negate())
                        .verify(packet, ctx),
                () -> MessageAcceptPipelineHelper.releaseQosOnFailure(packet),
                "权限不足/在黑名单中/不是群成员/被禁言/发送方和接收方相同, 请知悉。该消息 {} 被忽略");
    }

    /** QoS 重入由公共门闸处理，群聊仅提供本业务的恢复动作。 */
    @Override
    public boolean qosPreHandle(ChannelHandlerContext ctx, Packet packet) {
        return QosCommittedDeliverySupport.handle(ctx, packet,
                com.ouyunc.repository.DefaultRepository.INSTANCE, this::completeCommittedDelivery);
    }

    @Override
    public Mono<Void> process(ChannelHandlerContext ctx, Packet packet) {
        log.debug("Processing group message...");
        AbstractBaseBiProcessor<Mono<Void>, ? extends Number> content = MessageServerContext.messageContentProcessorCache.get(packet.getMessage().getContentType());
        if (content != null) {
            // 插件内容处理器：内容安全 + 归档后再交给插件，与默认聊天路径语义一致
            return MessageAcceptPipelineHelper.archiveAfterContentReady(ctx, packet,
                    Mono.defer(() -> content.process(ctx, packet)));
        }
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getIngress().getAppKey();
        String groupId = message.getTo();
        boolean skipSenderMembership = IngressPacketHelper.isHttpPush(packet)
                && IngressPacketHelper.isSystemLikeSender(message);
        if (!skipSenderMembership && !repository().inGroup(appKey, message.getFrom(), groupId)) {
            log.error("发送方：{}, 不在群组：{} 中！群消息： {}", message.getFrom(), groupId, packet);
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "发送者不在群组中", "GroupMessageBiProcessor.process", packet);
            MessageSubmissionResponseHelper.rejected(ctx, packet, ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR);
            MessageAcceptPipelineHelper.releaseQosOnFailure(packet);
            return Mono.empty();
        }
        if (!normalizeGroupAtOrReject(packet, appKey, groupId)) {
            MessageSubmissionResponseHelper.rejected(ctx, packet, ExceptionCodeEnum.GROUP_AT_MENTION_INVALID_ERROR);
            MessageAcceptPipelineHelper.releaseQosOnFailure(packet);
            return Mono.empty();
        }
        if (!MessageContentNormalizer.normalizeReferencesOrReject(packet)) {
            MessageSubmissionResponseHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_REF_INVALID_ERROR);
            MessageAcceptPipelineHelper.releaseQosOnFailure(packet);
            return Mono.empty();
        }
        int contentType = message.getContentType();
        if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType) {
            return handleReadReceipt(ctx, packet);
        }
        if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType) {
            return handleWithdrawMessage(ctx, packet);
        }
        return MessageAcceptPipelineHelper.archiveAfterContentReady(ctx, packet,
                reactiveSaveGroupMessage(packet)
                        .flatMap(result -> MessageAcceptPipelineHelper.afterHotSave(ctx, packet, result,
                                () -> afterGroupFreshWrite(packet),
                                "群聊消息写入会话失败"))
                        .onErrorResume(error -> {
                            log.error("群聊消息持久化异常, packetId={}", packet.getPacketId(), error);
                            ExceptionReporter.reportSystem(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                                    "群聊持久化异常: " + error.getMessage(),
                                    "GroupMessageBiProcessor.process", packet, error);
                            MessageAcceptPipelineHelper.releaseQosOnFailure(packet);
                            MessageSubmissionResponseHelper.unknown(ctx, packet, ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR);
                            return Mono.empty();
                        }));
    }

    private void afterGroupFreshWrite(Packet packet) {
        completeGroupDelivery(packet);
    }

    private void completeCommittedDelivery(Packet packet) {
        int contentType = packet.getMessage().getContentType();
        if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType
                || MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType) {
            return;
        }
        completeGroupDelivery(packet);
    }

    /** 首次写入和 COMMITTED 重入共用。已完成的扇出不会再次推送。 */
    private void completeGroupDelivery(Packet packet) {
        CommittedDelivery.run(packet, lease -> {
            try {
                repository().saveLastMessageForSession(packet.getMessage().getTo(), packet,
                        MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                // 最后消息是可重建派生索引，失败不能阻断已提交消息的实时投递。
                log.warn("更新群聊最后消息失败，继续投递 packetId={}", packet.getPacketId(), e);
            }
            SenderReadCompletionSupport.complete(packet, IdentityType.GROUP);
            deliver(packet, lease);
        });
    }


    /**
     * 群已读回执：仅校验并写入 Redis/MQ（{@code SessionMessageOffset}），不向群成员广播，避免每人读一次产生 (N-1) 次推送风暴。
     * 发送方「已读」展示应走 HTTP 拉取各成员 offset 或产品层不做群聊逐条已读（见业务文档）。
     * 阅读方多端同步仍可通过 selfSync 投递给自己其它终端。
     */
    private Mono<Void> handleReadReceipt(ChannelHandlerContext ctx, Packet packet) {
        String sessionId = packet.getMessage().getTo();
        return repository().reactiveHandleOperation(ctx, packet,
                repository().reactiveLoadValidatedReadReceiptPackets(
                        packet, packet.getMessage().getTo(), IdentityType.GROUP, false),
                ExceptionCodeEnum.READ_RECEIPT_MESSAGE_VERIFY_ERROR,
                MqConstant.MQ_READ_RECEIPT_MESSAGE_TOPIC, sessionId,
                packets -> repository().reactiveReadReceiptMessage(
                        packet, IdentityType.GROUP,
                        MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP, packets),
                (ctx0, packet0) -> {
                    deliverGroupReadReceiptSelfSyncOnly(packet0);
                },
                ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR)
                .doOnNext(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        MessageAcceptPipelineHelper.qosAckOnSuccess(ctx, packet);
                    } else {
                        MessageSubmissionResponseHelper.unknown(ctx, packet, ExceptionCodeEnum.UNKNOWN_ERROR);
                        MessageAcceptPipelineHelper.releaseQosOnFailure(packet);
                    }
                })
                .then();
    }

    /** 群已读不回推全员，仅 selfSync 时同步阅读方其它设备 */
    private void deliverGroupReadReceiptSelfSyncOnly(Packet packet) {
        Message message = packet.getMessage();
        ClientInfo clientInfo = MessageServerContext.localClientInfo(message.getMetadata().getIngress().getAppKey(), message.getFrom());
        if (clientInfo != null && clientInfo.getSelfSync()) {
            deliver2Self(packet);
        }
    }


    private Mono<Void> handleWithdrawMessage(ChannelHandlerContext ctx, Packet packet) {
        String sessionId = packet.getMessage().getTo();
        // 获取当前撤销人员是否是群主或者管理员，他们是最大权限可以撤销所有成员的消息，当然也包括自己
        Set<String> leaderOrManagerIdentitySet = repository().groupManagerAndLeaderUsersIdentity(packet);
        boolean leaderOrManager = CollectionUtils.isNotEmpty(leaderOrManagerIdentitySet) && leaderOrManagerIdentitySet.contains(packet.getMessage().getFrom());
        return repository().reactiveHandleOperation(ctx, packet,
                repository().reactiveLoadWithdrawTargetPackets(
                        packet, sessionId, MessageIndexScopeEnum.CHANNEL_SESSION, !leaderOrManager),
                ExceptionCodeEnum.WITHDRAW_MESSAGE_VERIFY_ERROR,
                MqConstant.MQ_WITHDRAW_MESSAGE_TOPIC, sessionId,
                packets -> repository().reactiveWithdrawMessage(
                        packet, sessionId, MessageIndexScopeEnum.CHANNEL_SESSION, packets),
                (ctx0, packet0) -> {
                    Message msg = packet0.getMessage();
                    if (msg != null && msg.getMetadata() != null) {
                        String appKey = msg.getMetadata().getIngress().getAppKey();
                        if (StringUtils.isNoneBlank(appKey, sessionId)) {
                            repository().refreshSessionLastMessageAfterWithdraw(appKey, sessionId);
                        }
                    }
                    deliverWithdrawMessage(packet0);
                },
                ExceptionCodeEnum.WITHDRAW_MESSAGE_ERROR)
                .doOnNext(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        MessageAcceptPipelineHelper.qosAckOnSuccess(ctx, packet);
                    } else {
                        MessageSubmissionResponseHelper.unknown(ctx, packet, ExceptionCodeEnum.UNKNOWN_ERROR);
                        MessageAcceptPipelineHelper.releaseQosOnFailure(packet);
                    }
                })
                .then();
    }



    /**
     * 发送撤回消息给接收方。
     * <p>撤回是会话状态变更而非内容推送，必须触达全体成员，不受 {@code group-message.mode}
     * 推拉策略约束：PULL / 超阈值 PULL_PUSH 下只推 @ 列表会让撤回对绝大多数成员静默失效。
     * 发送方其它设备同样强制同步，与单聊 {@code forceSelfSync} 语义一致。</p>
     */
    private void deliverWithdrawMessage(Packet packet) {
        deliver2Self(packet);
        deliver2AllGroupMembers(packet, loadFullMembersOrEmpty(packet));
    }

    private void deliver(Packet packet, CommittedDelivery.DeliveryLease lease) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getIngress().getAppKey();
        ClientInfo clientInfo = MessageServerContext.localClientInfo(appKey, message.getFrom());
        if (clientInfo != null && clientInfo.getSelfSync()) {
            deliver2Self(packet);
        }
        GroupMessagePushModeEnum mode = MessageServerContext.serverProperties().getGroupMessagePushMode();
        if (GroupMessagePushModeEnum.PUSH.equals(mode)) {
            deliver2AllGroupMembers(packet, loadFullMembersOrEmpty(packet), lease);
            return;
        }
        if (GroupMessagePushModeEnum.PULL.equals(mode)) {
            deliverAtMentionsIfAny(packet, lease);
            return;
        }
        if (GroupMessagePushModeEnum.PULL_PUSH.equals(mode)) {
            long memberCount = repository().groupMemberCount(appKey, message.getTo());
            if (memberCount > MessageServerContext.serverProperties().getGroupMessageThreshold()) {
                deliverAtMentionsIfAny(packet, lease);
            } else {
                deliver2AllGroupMembers(packet, loadFullMembersOrEmpty(packet), lease);
            }
            return;
        }
        log.warn("暂不支持该消息推送模式:{}, 消息：{}", mode, packet);
    }

    /**
     * 发送消息给自己的其他客戶端
     *
     * @param packet
     */
    private void deliver2Self(Packet packet) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getIngress().getAppKey();
        List<LoginClientInfo> fromSelfLoginClientInfos = ClientHelper.onlineAll(appKey, message.getFrom(), packet.getDeviceType());
        if (CollectionUtils.isNotEmpty(fromSelfLoginClientInfos)) {
            MessageDeliveryPlanner.deliverOnlineClients(packet, fromSelfLoginClientInfos);
        }
    }


    /**
     * 保存群组消息
     */
    private Mono<SaveMessageOutcomeEnum> reactiveSaveGroupMessage(Packet packet) {
        Message message = packet.getMessage();
        return repository().reactiveSaveMessage(packet, message.getTo(), MessageContext.messageHotDataTtlMillis());
    }



    private void deliver2AllGroupMembers(Packet packet, Set<String> groupMembers) {
        MessageDeliveryPlanner.deliverGroupMembers(packet, groupMembers);
    }

    private void deliver2AllGroupMembers(Packet packet, Set<String> groupMembers,
                                         CommittedDelivery.DeliveryLease lease) {
        MessageDeliveryPlanner.deliverGroupMembers(packet, groupMembers, lease::renew);
    }

    private void deliverAtMentionsIfAny(Packet packet) {
        List<String> atList = packet.getMessage().getAt();
        if (CollectionUtils.isEmpty(atList)) {
            return;
        }
        if (MessageContentNormalizer.containsAtAll(atList)) {
            deliver2AllGroupMembers(packet, loadFullMembersOrEmpty(packet));
            return;
        }
        MessageDeliveryPlanner.deliverGroupMembers(packet, new HashSet<>(atList));
    }

    private void deliverAtMentionsIfAny(Packet packet, CommittedDelivery.DeliveryLease lease) {
        List<String> atList = packet.getMessage().getAt();
        if (CollectionUtils.isEmpty(atList)) {
            return;
        }
        if (MessageContentNormalizer.containsAtAll(atList)) {
            deliver2AllGroupMembers(packet, loadFullMembersOrEmpty(packet), lease);
            return;
        }
        MessageDeliveryPlanner.deliverGroupMembers(packet, new HashSet<>(atList), lease::renew);
    }

    private Set<String> loadFullMembersOrEmpty(Packet packet) {
        try {
            Set<String> members = repository().groupUsersIdentity(packet);
            return members == null ? Set.of() : members;
        } catch (GroupMembershipSupport.GroupMembershipLoadException e) {
            log.error("枚举群成员失败, groupId={} packetId={}",
                    packet.getMessage().getTo(), packet.getPacketId(), e);
            scheduleFanoutRecovery(packet);
            // 不能把“成员尚未加载出来”当作空群并把 delivery-done 写成完成。
            // 抛出后会释放本次 owner，客户端重试和本机有界恢复任务均可继续补投。
            throw e;
        }
    }

    /**
     * 群消息已经归档和热写，成员枚举暂时失败时做有界内存补投；节点重启后客户端仍可按会话索引补拉。
     * 补投只重新读取成员并执行实时扇出，不重复归档、热写、未读累加或发送方 ACK。
     */
    private void scheduleFanoutRecovery(Packet packet) {
        if (packet == null || packet.getMessage() == null || packet.getMessage().getMetadata() == null) {
            return;
        }
        String appKey = packet.getMessage().getMetadata().getIngress().getAppKey();
        String recoveryKey = appKey + MessageConstant.COLON + packet.getPacketId();
        if (PENDING_FANOUT_RECOVERY.size() >= MessageConstant.GROUP_FANOUT_RECOVERY_MAX_PENDING) {
            log.error("群扇出补投队列已满, packetId={} pending={}",
                    packet.getPacketId(), PENDING_FANOUT_RECOVERY.size());
            return;
        }
        if (PENDING_FANOUT_RECOVERY.putIfAbsent(recoveryKey, Boolean.TRUE) != null) {
            return;
        }
        scheduleFanoutRecoveryAttempt(appKey, packet.getPacketId(), recoveryKey, 1);
    }

    private void scheduleFanoutRecoveryAttempt(String appKey, long packetId, String recoveryKey, int attempt) {
        long delayMs = MessageConstant.GROUP_FANOUT_RECOVERY_DELAY_MS * attempt;
        io.netty.util.Timeout timeout = ScheduleTimer.scheduleOnce(() -> {
            try {
                ThreadPoolManager.messageSendExecutor().execute(
                        () -> runFanoutRecoveryAttempt(appKey, packetId, recoveryKey, attempt));
            } catch (RejectedExecutionException error) {
                PENDING_FANOUT_RECOVERY.remove(recoveryKey);
                log.error("群扇出补投执行器已满, packetId={} attempt={}", packetId, attempt, error);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
        if (timeout == null) {
            PENDING_FANOUT_RECOVERY.remove(recoveryKey);
        }
    }

    private void runFanoutRecoveryAttempt(String appKey, long packetId, String recoveryKey, int attempt) {
        try {
            List<Packet> stored = repository().getPackets(appKey, List.of(packetId));
            if (CollectionUtils.isEmpty(stored) || stored.getFirst() == null) {
                throw new GroupMembershipSupport.GroupMembershipLoadException("补投消息正文暂不可用");
            }
            Packet packet = stored.getFirst();
            // 恢复任务也必须竞争同一 delivery owner；不能绕过状态机直接扇出，否则会与客户端重试重复投递。
            completeGroupDelivery(packet);
            PENDING_FANOUT_RECOVERY.remove(recoveryKey);
            log.info("群扇出补投成功, packetId={} attempt={}", packet.getPacketId(), attempt);
        } catch (GroupMembershipSupport.GroupMembershipLoadException error) {
            if (attempt < MessageConstant.GROUP_FANOUT_RECOVERY_MAX_ATTEMPTS) {
                scheduleFanoutRecoveryAttempt(appKey, packetId, recoveryKey, attempt + 1);
                return;
            }
            PENDING_FANOUT_RECOVERY.remove(recoveryKey);
            log.error("群扇出补投达到上限，接收方需按会话索引补拉, packetId={} attempts={}",
                    packetId, attempt, error);
        } catch (DeliveryRunBusyException error) {
            // 客户端重试或其他恢复线程正在持有 delivery owner。保留补投项等待下一轮，
            // 下次执行会在 done 状态上幂等返回，不会与当前 owner 重复扇出。
            if (attempt < MessageConstant.GROUP_FANOUT_RECOVERY_MAX_ATTEMPTS) {
                scheduleFanoutRecoveryAttempt(appKey, packetId, recoveryKey, attempt + 1);
                return;
            }
            PENDING_FANOUT_RECOVERY.remove(recoveryKey);
            log.warn("群扇出补投持续等待其他 delivery owner, packetId={} attempts={}",
                    packetId, attempt, error);
        } catch (RuntimeException error) {
            PENDING_FANOUT_RECOVERY.remove(recoveryKey);
            log.error("群扇出补投异常, packetId={} attempt={}", packetId, attempt, error);
        }
    }

    /**
     * 校验并规范化群 @ 列表；只点查 @ 对象，不拉全群。
     */
    private boolean normalizeGroupAtOrReject(Packet packet, String appKey, String groupId) {
        Message message = packet.getMessage();
        List<String> at = message.getAt();
        if (CollectionUtils.isEmpty(at)) {
            return true;
        }
        try {
            List<String> explicit = MessageContentNormalizer.explicitAtMemberIds(at);
            Set<String> confirmed = repository().presentInGroup(appKey, groupId, explicit);
            message.setAt(MessageContentNormalizer.normalizeAt(at, confirmed));
            return true;
        } catch (GroupMembershipSupport.GroupMembershipLoadException | IllegalArgumentException ex) {
            log.warn("群@校验失败: {} | packet={}", ex.getMessage(), packet);
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.GROUP_AT_MENTION_INVALID_ERROR, ex.getMessage(), "GroupMessageBiProcessor.process", packet);
            return false;
        }
    }
}
