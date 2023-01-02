package com.ouyunc.im.processor;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.SystemClock;
import cn.hutool.json.JSONUtil;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.ReadReceiptContent;
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.utils.SocketAddressUtil;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 已读回执处理器，需要客户端配合使用
 */
public class ReadReceiptMessageProcessor extends AbstractMessageProcessor {
    private static Logger log = LoggerFactory.getLogger(ReadReceiptMessageProcessor.class);

    @Override
    public MessageEnum messageType() {
        return MessageEnum.IM_READ_RECEIPT;
    }

    /**
     * 已读回执消息处理器，私聊和群聊都会走这里，进行判断处理,支持批量已读处理，减少网络请求次数，提高效率
     * 目前支持私聊和群组
     *
     * @param ctx
     * @param packet
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("ReadReceiptMessageProcessor 正在处理已读回执消息packet: {}", packet);
        fireProcess(ctx, packet, (ctx0, packet0) -> {
            // 获取需要回执的消息id
            Message message = (Message) packet.getMessage();
            // 未开启db
            ExtraMessage extraMessage = JSONUtil.toBean(message.getExtra(), ExtraMessage.class);
            if (extraMessage == null) {
                extraMessage = new ExtraMessage();
            }
            // 接收方
            String to = message.getTo();
            // 是传递过来的,判断该消息最终服务地址是否是本机
            if (extraMessage.isDelivery()) {
                if (IMServerContext.SERVER_CONFIG.getLocalServerAddress().equals(extraMessage.getTargetServerAddress()) || !IMServerContext.SERVER_CONFIG.isClusterEnable()) {
                    MessageHelper.sendMessage(packet, IdentityUtil.generalComboIdentity(to, extraMessage.getDeviceEnum().getName()));
                    return;
                }
                MessageHelper.deliveryMessage(packet, SocketAddressUtil.convert2SocketAddress(extraMessage.getTargetServerAddress()));
                return;
            }
            // 判断是否是已读消息内容类型
            if (MessageContentEnum.READ_RECEIPT_CONTENT.type() != message.getContentType()) {
                return;
            }
            List<ReadReceiptContent> readReceiptList = JSONUtil.toList(message.getContent(), ReadReceiptContent.class);
            // 不做处理
            if (CollectionUtil.isEmpty(readReceiptList)) {
                return;
            }
            DbHelper.writeMessageReadReceipt(message.getFrom(), readReceiptList);
            // 无论私聊还是群聊，根据所有消息的发送者进行分组，传递分批传递消息
            readReceiptList.stream().collect(Collectors.groupingBy(ReadReceiptContent::getIdentity)).forEach((identity, readReceiptContents) -> {
                // 判断from是否在线,如果不在线，则将该批次回执消息存到对应客户端的离线信箱中，稍后发布,注意这里涉及接受者多设备端，不考虑发送者多设备端（影响不大）
                // 重新封装packet消息,进行发送
                message.setTo(identity);
                message.setContent(JSONUtil.toJsonStr(readReceiptContents));
                // 获取该客户端在线的所有客户端，进行推送消息已读
                List<LoginUserInfo> loginUserInfos = UserHelper.onlineAll(identity);
                if (CollectionUtil.isEmpty(loginUserInfos)) {
                    // 存入离线信箱
                    DbHelper.write2OfflineTimeline(packet, identity, SystemClock.now());
                } else {
                    // 转发给某个客户端的各个设备端
                    MessageHelper.send2MultiDevices(packet, loginUserInfos);
                }

            });
        });
    }


}
