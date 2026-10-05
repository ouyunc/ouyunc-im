package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqArchiveRouting;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.model.RequestEventContext;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.exception.ExternalDeliveryConfirmException;
import com.ouyunc.repository.DefaultRepository;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 请求的可重入完成步骤：读取已持久化快照 → 等待领域 MQ 确认 → 提交在线通知 → 标记完成。
 * broker 确认与完成标记之间退出时允许重复发布；消费端按请求会话/消息幂等处理。
 * 不能用重试包推导申请状态，否则已撤权、已拒绝或新申请会污染第一次命令。
 */
public final class RequestCompletionHelper {
    private RequestCompletionHelper() { }

    public static void complete(Packet retry) {
        try {
            completeStored(retry);
        } catch (ExternalDeliveryConfirmException error) {
            throw error;
        } catch (RuntimeException error) {
            // 前置判重也会调用这里；统一转为可回复 UNKNOWN 的异常，不能在 Mono 建链前逃逸。
            throw new ExternalDeliveryConfirmException("请求完成状态暂不可确认", error);
        }
    }

    private static void completeStored(Packet retry) {
        DefaultRepository repository = DefaultRepository.INSTANCE;
        if (repository.isDeliveryFinished(retry)) {
            return;
        }
        String appKey = retry.getMessage().getMetadata().getIngress().getAppKey();
        List<Packet> stored = repository.getPackets(appKey, List.of(retry.getPacketId()));
        if (stored == null || stored.size() != 1 || stored.getFirst() == null
                || stored.getFirst().getMessage().getMetadata().getRequestEventContext() == null) {
            throw new ExternalDeliveryConfirmException("请求热写快照缺失，不能确认领域命令完成", null);
        }
        Packet command = stored.getFirst();
        CommittedDelivery.run(command, () -> publishAndNotify(command, appKey));
    }

    private static void publishAndNotify(Packet packet, String appKey) {
        String topic = MqArchiveRouting.isFriendRequestType(packet.getMessageType())
                ? MqConstant.MQ_FRIEND_REQUEST_TOPIC : MqConstant.MQ_GROUP_REQUEST_TOPIC;
        try {
            if (!DefaultRepository.INSTANCE.isRequestCommandConfirmed(packet)) {
                DefaultRepository.INSTANCE.publishPacketConfirmed(topic, MqArchiveRouting.partitionKey(packet), packet)
                        .get(MessageConstant.MESSAGE_ARCHIVE_CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                DefaultRepository.INSTANCE.confirmRequestCommand(packet);
            }
            RequestNotifyHelper.dispatchCommitted(packet, appKey, recipients(packet));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ExternalDeliveryConfirmException("请求领域命令确认被中断", error);
        } catch (Exception error) {
            throw new ExternalDeliveryConfirmException("请求领域命令或通知未完成", error);
        }
    }

    /** 通知人选择与原请求策略一致；管理员名单实时读取，避免向已离群的旧管理员发送通知。 */
    private static Set<String> recipients(Packet packet) {
        byte type = packet.getMessageType();
        var message = packet.getMessage();
        RequestEventContext context = message.getMetadata().getRequestEventContext();
        if (MqArchiveRouting.isFriendRequestType(type)) {
            boolean autoJoin = type == MessageTypeEnum.ONE_2_ONE_FRIEND_REQUEST_JOIN.getType()
                    && RequestSessionProgress.AGREEING.value().equals(context.getProgress());
            return RequestNotifyHelper.userOnly(autoJoin ? message.getFrom() : message.getTo());
        }
        boolean approval = type == MessageTypeEnum.GROUP_REQUEST_AGREE.getType()
                || type == MessageTypeEnum.GROUP_REQUEST_REFUSE.getType();
        if (!approval && RequestSessionProgress.AGREEING.value().equals(context.getProgress())) {
            return RequestNotifyHelper.userOnly(context.getJoiner());
        }
        if (type == MessageTypeEnum.GROUP_REQUEST_INVITE_JOIN.getType()
                && GroupJoinerProcessStatus.PENDING.value().equals(context.getJoinerProcessStatus())) {
            return RequestNotifyHelper.userOnly(context.getJoiner());
        }
        Set<String> managers = RequestNotifyHelper.copyExcept(
                DefaultRepository.INSTANCE.groupManagerAndLeaderUsersIdentity(packet), message.getFrom());
        return approval ? RequestNotifyHelper.withUser(managers, context.getJoiner()) : managers;
    }
}
