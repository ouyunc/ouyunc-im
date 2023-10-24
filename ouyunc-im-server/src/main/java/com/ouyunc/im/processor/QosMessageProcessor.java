package com.ouyunc.im.processor;


import com.alibaba.fastjson2.JSON;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.enums.DeviceEnum;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.Target;
import com.ouyunc.im.packet.message.content.ClientQosNotifyContent;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 客户端应答消息处理器（qos 保障，客户端收到消息后会发给另外一端收到消息的应答）
 **/
public class QosMessageProcessor extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(QosMessageProcessor.class);


    @Override
    public MessageEnum messageType() {
        return MessageEnum.IM_QOS;
    }

    /**
     * @Author fangzhenxun
     * @Description ack业务处理, 如果离线需要伪造对方客户端发送消息接收方已经收到消息
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("正在处理客户端packet: {} 的ack...", packet);
        fireProcess(ctx, packet,(ctx0, packet0)->{
            Message message = (Message) packet.getMessage();
            // 判断消息内容类型是否是客户端回应的消息投递成功的ack
            if (MessageContentEnum.CLIENT_QOS_NOTIFY_CONTENT.type() != message.getContentType()) {
                return;
            }
            String to = message.getTo();
            // 这里需要前端传递两个值一个消息id一个消息目标的服务器登录设备类型（客户端可以从接收的消息获取）
            ClientQosNotifyContent clientReplyAckContent = JSON.parseObject(message.getContent(), ClientQosNotifyContent.class);
            byte loginDeviceType = clientReplyAckContent.getDeviceType();
            // 首先判断消息在线还是离线,只回复发送者设备
            LoginUserInfo loginUserInfo = UserHelper.online(to, loginDeviceType);
            // 不在线,直接结束
            if (loginUserInfo == null) {
                return;
            }
            // 获取目标服务对应的服务地址
            String targetServerAddress = loginUserInfo.getLoginServerAddress();
            Target target = Target.newBuilder().targetIdentity(to).deviceEnum(DeviceEnum.getDeviceEnumByValue(loginDeviceType)).targetServerAddress(targetServerAddress).build();
            if (IMServerContext.SERVER_CONFIG.getLocalServerAddress().equals(targetServerAddress) || !IMServerContext.SERVER_CONFIG.isClusterEnable()) {
                MessageHelper.sendMessage(packet, target);
                return;
            }
            MessageHelper.deliveryMessage(packet, target);
        });
        // 这里如果返回ack给发送方，如果发送方不在线了，则不进行消息存储，问题不大。（可能造成的原因是：等发送方恢复在线了，可能会重复发送一条之前已经成功的消息，这个时候客户端需要做去重处理）
    }


}
