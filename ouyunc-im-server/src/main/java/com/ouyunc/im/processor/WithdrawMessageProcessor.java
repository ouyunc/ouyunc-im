package com.ouyunc.im.processor;

import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.utils.SystemClock;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


/**
 * 撤回消息，撤回规则，比如超过多久不能撤回由客户端来确定
 */
public class WithdrawMessageProcessor extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(WithdrawMessageProcessor.class);

    @Override
    public MessageEnum messageType() {
        return MessageEnum.IM_WITHDRAW;
    }

    /**
     * 做具体的撤回逻辑
     * @param ctx
     * @param packet
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("WithdrawMessageProcessor 撤回处理器正在处理消息：{}", packet);
        fireProcess(ctx, packet,(ctx0, packet0)->{
            Message message = (Message) packet.getMessage();
            // 修改发件箱和收件箱的消息状态

            // 下面是对集群以及qos消息可靠进行处理
            String from = message.getFrom();
            // 根据to从分布式缓存中取出targetServerAddress目标地址
            String to = message.getTo();
            // 将消息写到发件箱和及接收方的收件箱
            long timestamp = SystemClock.now();
            DbHelper.write2SendTimeline(packet, from, timestamp);
            DbHelper.write2ReceiveTimeline(packet, to, timestamp);
            // 发送给自己的其他端
            List<LoginUserInfo> fromLoginUserInfos = UserHelper.onlineAll(from, packet.getDeviceType());
            // 排除自己，发给其他端
            // 转发给自己客户端的各个设备端
            MessageHelper.send2MultiDevices(packet, fromLoginUserInfos);
            // 获取该客户端在线的所有客户端，进行推送消息已读
            List<LoginUserInfo> toLoginUserInfos = UserHelper.onlineAll(to);
            if (CollectionUtils.isEmpty(toLoginUserInfos)) {
                // 存入离线消息，不以设备来区分
                DbHelper.write2OfflineTimeline(packet, to, timestamp);
                return;
            }
            // 转发给某个客户端的各个设备端
            MessageHelper.send2MultiDevices(packet, toLoginUserInfos);
        });
    }
}
