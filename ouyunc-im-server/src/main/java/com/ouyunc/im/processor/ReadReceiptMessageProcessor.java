package com.ouyunc.im.processor;


import com.alibaba.fastjson2.JSON;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.constant.enums.MessageTypeEnum;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.InnerExtraData;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.ReadReceiptContent;
import com.ouyunc.im.utils.SystemClock;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
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
    public MessageTypeEnum messageType() {
        return MessageTypeEnum.IM_READ_RECEIPT;
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
            ExtraMessage extraMessage = JSON.parseObject(message.getExtra(), ExtraMessage.class);
            InnerExtraData innerExtraData = extraMessage.getInnerExtraData();
            String appKey = innerExtraData.getAppKey();
            // 判断是否是已读消息内容类型
            if (MessageContentEnum.READ_RECEIPT_CONTENT.type() != message.getContentType()) {
                return;
            }
            List<ReadReceiptContent> readReceiptList = JSON.parseArray(message.getContent(), ReadReceiptContent.class);
            // 不做处理
            if (CollectionUtils.isEmpty(readReceiptList)) {
                return;
            }
            DbHelper.writeMessageReadReceipt(appKey, message.getFrom(), readReceiptList);
            // 无论私聊还是群聊，根据所有消息的发送者进行分组，传递分批传递消息
            readReceiptList.stream().collect(Collectors.groupingBy(ReadReceiptContent::getIdentity)).forEach((identity, readReceiptContents) -> {
                // 判断from是否在线,如果不在线，则将该批次回执消息存到对应客户端的离线信箱中，稍后发布,注意这里涉及接受者多设备端，不考虑发送者多设备端（影响不大）
                // 重新封装packet消息,进行发送
                message.setTo(identity);
                message.setContent(JSON.toJSONString(readReceiptContents));
                // 无论是否在线都会写入离线消息表，然后收到消息后通过ack开确认是否收到消息
                DbHelper.write2OfflineTimeline(appKey, packet, identity, SystemClock.now());
                // 获取该客户端在线的所有客户端，进行推送消息已读
                List<LoginUserInfo> loginUserInfos = UserHelper.onlineAll(appKey, identity);
                if (CollectionUtils.isNotEmpty(loginUserInfos)) {
                    // 转发给某个客户端的各个设备端
                    MessageHelper.send2MultiDevices(packet, loginUserInfos);
                }
            });
        });
    }


}
