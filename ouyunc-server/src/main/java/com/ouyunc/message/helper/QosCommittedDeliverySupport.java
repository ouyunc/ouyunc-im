package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqArchiveRouting;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.exception.ExternalDeliveryConfirmException;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.function.Consumer;

/**
 * QoS 已提交消息的统一重入门闸。
 *
 * <p>本类只负责判重、异常转换与 ACK 时机。具体业务通过回调补齐派生索引、领域事件和实时投递，
 * 避免通用处理器基类感知单聊、群聊、客服或请求业务。</p>
 */
public final class QosCommittedDeliverySupport {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QosCommittedDeliverySupport.class);

    private QosCommittedDeliverySupport() {
    }

    /**
     * 命中 COMMITTED 时执行可选恢复动作，完成后 ACK；恢复失败时返回 UNKNOWN。
     *
     * @param recovery 没有提交后恢复动作时传 {@code null}
     * @return {@code true} 表示本次消息已在判重路径处理，调用方不得再进入主流程
     */
    public static boolean handle(ChannelHandlerContext ctx, Packet packet, DefaultRepository repository,
                                 Consumer<Packet> recovery) {
        if (!eligible(packet)) {
            return false;
        }
        LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(
                ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        String channelLoginIdentity = loginClientInfo != null ? loginClientInfo.getIdentity() : null;
        if (!repository.checkDup(packet, channelLoginIdentity)) {
            return false;
        }
        try {
            if (recovery != null) {
                recovery.accept(loadCommittedPacket(repository, packet));
            }
        } catch (com.ouyunc.base.exception.DeliveryRunBusyException busy) {
            log.debug("已提交消息正在恢复, packetId={} messageId={} type={}",
                    packet.getPacketId(), packet.getMessage().getId(), packet.getMessageType());
            MessageSubmissionResponseHelper.retryLater(ctx, packet, ExceptionCodeEnum.UNKNOWN_ERROR);
            return true;
        } catch (RuntimeException error) {
            // 恢复链包含 Redis、数据库和在线投递，任何运行时异常都表示完成状态暂不可确认。
            // 此处统一返回 UNKNOWN，禁止异常穿透后既无 ACK 也无明确重试语义。
            log.error("已提交消息恢复失败, packetId={} messageId={} type={}",
                    packet.getPacketId(), packet.getMessage().getId(), packet.getMessageType(), error);
            MessageSubmissionResponseHelper.unknown(ctx, packet, ExceptionCodeEnum.UNKNOWN_ERROR);
            return true;
        }
        MessageSubmissionResponseHelper.accepted(ctx, packet);
        return true;
    }

    /**
     * COMMITTED 重入必须使用首次正式 Packet 完成派生索引和投递。
     * 客户端重试包仍是原始内容，可能尚未经过 MASK、引用/@ 规范化或客服入口身份改写，
     * 直接拿它恢复会让热数据与投递内容不一致。
     */
    private static Packet loadCommittedPacket(DefaultRepository repository, Packet retry) {
        if (retry == null || retry.getMessage() == null || retry.getMessage().getMetadata() == null) {
            throw new ExternalDeliveryConfirmException("已提交消息重入缺少正式身份", null);
        }
        try {
            String appKey = retry.getMessage().getMetadata().getIngress().getAppKey();
            List<Packet> packets = repository.getPackets(appKey, List.of(retry.getPacketId()));
            if (CollectionUtils.isEmpty(packets) || packets.getFirst() == null) {
                throw new ExternalDeliveryConfirmException("已提交消息正文暂不可用, packetId="
                        + retry.getPacketId(), null);
            }
            Packet committed = packets.getFirst();
            if (committed.getPacketId() != retry.getPacketId()
                    || committed.getMessage() == null
                    || !StringUtils.equals(committed.getMessage().getId(), retry.getMessage().getId())) {
                throw new ExternalDeliveryConfirmException("已提交消息正文与幂等身份不一致, packetId="
                        + retry.getPacketId(), null);
            }
            return committed;
        } catch (ExternalDeliveryConfirmException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new ExternalDeliveryConfirmException("读取已提交消息正文失败, packetId="
                    + retry.getPacketId(), error);
        }
    }

    private static boolean eligible(Packet packet) {
        if (packet == null) {
            return false;
        }
        Message message = packet.getMessage();
        if (message == null || StringUtils.isBlank(message.getId())) {
            return false;
        }
        // 撤回、已读等控制操作必须进入自己的快照恢复链。
        return !MqArchiveRouting.isSessionControl(packet);
    }
}
