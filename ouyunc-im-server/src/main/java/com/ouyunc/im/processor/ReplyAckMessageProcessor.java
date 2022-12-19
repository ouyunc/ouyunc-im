package com.ouyunc.im.processor;


import cn.hutool.json.JSONUtil;
import com.ouyunc.im.constant.enums.DeviceEnum;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.ClientReplyAckContent;
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.utils.SocketAddressUtil;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 应答消息处理器（qos 保障，客户端收到消息后会发给另外一端收到消息的应答）
 * @Version V3.0
 **/
public class ReplyAckMessageProcessor extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(ReplyAckMessageProcessor.class);


    @Override
    public MessageEnum messageType() {
        return MessageEnum.IM_REPLY_ACK;
    }

    /**
     * @Author fangzhenxun
     * @Description ack业务处理
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("正在处理客户端packet: {} 的ack...", packet);
        fireProcess(ctx, packet,(ctx0, packet0)->{
            Message message = (Message) packet.getMessage();
            ExtraMessage extraMessage = JSONUtil.toBean(message.getExtra(), ExtraMessage.class);
            // 判断是否是传递消息
            if (extraMessage == null) {
                extraMessage = new ExtraMessage();
            }
            String comboIdentity = null;
            String targetServerAddress = null;
            // 如果该消息是从其他服务传递过来的，则直接进行判断就行了，否则进行多端的转发或发送
            if (extraMessage.isDelivery()) {
                // 如果是从其他服务传递过来的消息，则直接从目标服务取
                targetServerAddress = extraMessage.getTargetServerAddress();
            }else {
                // 判断消息内容类型是否是客户端回应的消息投递成功的ack
                if (MessageContentEnum.CLIENT_REPLY_ACK_CONTENT.type() != message.getContentType()) {
                    return;
                }
                // 这里需要前端传递两个值一个消息id一个消息目标的服务器地址
                ClientReplyAckContent clientReplyAckContent = JSONUtil.toBean(message.getContent(), ClientReplyAckContent.class);
                byte deviceType = clientReplyAckContent.getDeviceType();
                comboIdentity = IdentityUtil.generalComboIdentity(message.getTo(), deviceType);
                // 首先判断消息在线还是离线,多端设备
                LoginUserInfo onlineUserInfo = UserHelper.online(comboIdentity);
                // 不在线,直接结束
                if (onlineUserInfo == null) {
                    return;
                }
                // 获取目标服务对应的服务地址
                targetServerAddress = onlineUserInfo.getLoginServerAddress();
                // 设置设备类型
                extraMessage.setDeviceEnum(DeviceEnum.getDeviceEnumByValue(clientReplyAckContent.getDeviceType()));
            }
            // 判断该消息是否是开启集群,或者目标服务是本机
            if (IMServerContext.SERVER_CONFIG.getLocalServerAddress().equals(targetServerAddress) || !IMServerContext.SERVER_CONFIG.isClusterEnable()) {
                // 清除扩展信息
                message.setExtra(null);
                MessageHelper.sendMessage(packet, comboIdentity);
                return;
            }
            MessageHelper.deliveryMessage(packet, SocketAddressUtil.convert2SocketAddress(targetServerAddress));
        });
        // 这里如果返回ack给发送方，如果发送方不在线了，则不进行消息存储，问题不大。（可能造成的原因是：等发送方恢复在线了，可能会重复发送一条之前已经成功的消息，这个时候解后端需要做去重处理）
    }


}
