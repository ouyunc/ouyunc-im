package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqArchiveRouting;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.core.exception.DeliveryRunBusyException;
import com.ouyunc.core.exception.ExternalDeliveryConfirmException;
import com.ouyunc.repository.Repository;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;

import java.util.function.Consumer;

/**
 * QoS 已提交消息的统一重入门闸。
 *
 * <p>本类只负责判重、异常转换与 ACK 时机。具体业务通过回调补齐派生索引、领域事件和实时投递，
 * 避免通用处理器基类感知单聊、群聊、客服或请求业务。</p>
 */
public final class QosCommittedDeliverySupport {

    private QosCommittedDeliverySupport() {
    }

    /**
     * 命中 COMMITTED 时执行可选恢复动作，完成后 ACK；恢复失败时返回 UNKNOWN。
     *
     * @param recovery 没有提交后恢复动作时传 {@code null}
     * @return {@code true} 表示本次消息已在判重路径处理，调用方不得再进入主流程
     */
    public static boolean handle(ChannelHandlerContext ctx, Packet packet, Repository repository,
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
                recovery.accept(packet);
            }
        } catch (ExternalDeliveryConfirmException | DeliveryRunBusyException error) {
            MessageSubmissionResponseHelper.unknown(ctx, packet, ExceptionCodeEnum.MQ_PERSISTENCE_ERROR);
            return true;
        }
        MessageSubmissionResponseHelper.accepted(ctx, packet);
        return true;
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
