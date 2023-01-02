package com.ouyunc.im.processor;

import cn.hutool.json.JSONUtil;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.OfflineContent;
import com.ouyunc.im.utils.IdentityUtil;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @Author fangzhenxun
 * @Description: 处理客户端拉取离线消息
 * @Version V3.0
 **/
public class OfflineMessageProcessor extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(OfflineMessageProcessor.class);

    @Override
    public MessageEnum messageType() {
        return MessageEnum.IM_OFFLINE;
    }

    /**
     * @Author fangzhenxun
     * @Description 处理离线消息,多端设备不能多次拉取
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("OfflineMessageProcessor 正在处理离线消息packet: {}", packet);
        Message message = (Message) packet.getMessage();
        if (MessageContentEnum.OFFLINE_CONTENT.type() != message.getContentType()) {
            return;
        }
        OfflineContent offlineContent = JSONUtil.toBean(message.getContent(), OfflineContent.class);
        // 拉取离线消息（根据最近消息顺序拉取）,按需拉取或全量按顺序拉取
        List<Packet> offlineMessageList = DbHelper.pullOfflineMessage(message);
        offlineContent.setPacketList(offlineMessageList);
        message.setContent(JSONUtil.toJsonStr(offlineContent));
        MessageHelper.sendMessage(packet, IdentityUtil.generalComboIdentity(message.getFrom(), packet.getDeviceType()));
    }


}
