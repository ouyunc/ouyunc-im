package com.ouyunc.im.processor;


import cn.hutool.json.JSONUtil;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.InnerExtraData;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.ClientReplyAckContent;
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.utils.SocketAddressUtil;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 客户端应答消息处理器（qos 保障，客户端收到消息后会发给另外一端收到消息的应答）
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
            InnerExtraData innerExtraData = JSONUtil.toBean(message.getExtra(), InnerExtraData.class);
            // 判断是否是传递消息
            if (innerExtraData == null) {
                innerExtraData = new InnerExtraData();
            }
            // 根据to从分布式缓存中取出targetServerAddress目标地址
            String to = message.getTo();
            // 这里需要前端传递两个值一个消息id一个消息目标的服务器登录设备类型（客户端可以从哪接收的消息获取）
            ClientReplyAckContent clientReplyAckContent = JSONUtil.toBean(message.getContent(), ClientReplyAckContent.class);
            byte deviceType = clientReplyAckContent.getDeviceType();
            String comboIdentity = IdentityUtil.generalComboIdentity(to, deviceType);
            String targetServerAddress;
            // 如果该消息是从其他服务传递过来的，则直接进行判断就行了，否则进行多端的转发或发送
            if (innerExtraData.isDelivery()) {
                // 如果是从其他服务传递过来的消息，则直接从目标服务取
                targetServerAddress = innerExtraData.getTargetServerAddress();
            }else {
                // 判断消息内容类型是否是客户端回应的消息投递成功的ack
                if (MessageContentEnum.CLIENT_REPLY_ACK_CONTENT.type() != message.getContentType()) {
                    return;
                }
                // 首先判断消息在线还是离线,只回复发送者设备
                LoginUserInfo loginUserInfo = UserHelper.online(to, deviceType);
                // 不在线,直接结束
                if (loginUserInfo == null) {
                    return;
                }
                // 获取目标服务对应的服务地址
                targetServerAddress = loginUserInfo.getLoginServerAddress();
            }
            // 判断该消息是否是开启集群,或者目标服务是本机
            if (IMServerContext.SERVER_CONFIG.getLocalServerAddress().equals(targetServerAddress) || !IMServerContext.SERVER_CONFIG.isClusterEnable()) {
                MessageHelper.sendMessage(packet, comboIdentity);
                return;
            }
            MessageHelper.deliveryMessage(packet, SocketAddressUtil.convert2SocketAddress(targetServerAddress));
        });
        // 这里如果返回ack给发送方，如果发送方不在线了，则不进行消息存储，问题不大。（可能造成的原因是：等发送方恢复在线了，可能会重复发送一条之前已经成功的消息，这个时候客户端需要做去重处理）
    }


}
