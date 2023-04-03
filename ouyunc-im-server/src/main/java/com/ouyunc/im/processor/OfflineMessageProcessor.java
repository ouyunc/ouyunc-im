package com.ouyunc.im.processor;

import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.utils.IdentityUtil;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 处理客户端拉取离线消息, 建议离线消息拉取走 http 服务
 * @Version V3.0
 * 为最大简单优化IM（只做消息的高效转发）服务系统，在v4.0版本将去除，建议使用http接口的形式来获取离线消息
 **/
@Deprecated
public class OfflineMessageProcessor extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(OfflineMessageProcessor.class);

    @Override
    public MessageEnum messageType() {
        return MessageEnum.IM_OFFLINE;
    }

    /**
     * @Author fangzhenxun
     * @Description 处理离线消息,多端设备不能多次拉取,
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("OfflineMessageProcessor 正在处理离线消息packet: {}", packet);
        Message message = (Message) packet.getMessage();
        if (MessageContentEnum.OFFLINE_CONTENT.type() != message.getContentType() && MessageContentEnum.UNREAD_CONTENT.type() != message.getContentType()) {
            return;
        }
        // 拉取离线消息（根据最近消息顺序拉取）,按需拉取或全量按顺序拉取
        DbHelper.pullOfflineMessage(message);
        MessageHelper.sendMessage(packet, IdentityUtil.generalComboIdentity(message.getFrom(), packet.getDeviceType()));
    }


}
