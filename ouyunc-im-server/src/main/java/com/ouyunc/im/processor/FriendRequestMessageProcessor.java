package com.ouyunc.im.processor;

import cn.hutool.core.collection.CollectionUtil;
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
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.utils.SocketAddressUtil;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 好友申请/拒绝/同意处理器；
 * 删除好友和黑名单通过http服务器来实现，这里不做处理
 */
public class  FriendRequestMessageProcessor extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(FriendRequestMessageProcessor.class);



    @Override
    public MessageEnum messageType() {
        return MessageEnum.IM_FRIEND_REQUEST;
    }

    /**
     * 逻辑处理
     * @param ctx
     * @param packet
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("FriendRequestMessageProcessor 正在处理好友请求消息packet: {}", packet);
        fireProcess(ctx, packet, (ctx0, packet0)->{
            Message message = (Message) packet.getMessage();
            ExtraMessage extraMessage = JSONUtil.toBean(message.getExtra(), ExtraMessage.class);
            if (extraMessage == null) {
                extraMessage = new ExtraMessage();
            }
            // 下面是对集群以及qos消息可靠进行处理
            String from = message.getFrom();
            // 根据to从分布式缓存中取出targetServerAddress目标地址
            String to = message.getTo();
            // 判断是否从其他服务路由过来的额消息
            if (extraMessage.isDelivery()) {
                if (IMServerContext.SERVER_CONFIG.getLocalServerAddress().equals(extraMessage.getTargetServerAddress()) || !IMServerContext.SERVER_CONFIG.isClusterEnable()) {
                    MessageHelper.sendMessage(packet, IdentityUtil.generalComboIdentity(to, extraMessage.getDeviceEnum().getName()));
                    return;
                }
                MessageHelper.deliveryMessage(packet, SocketAddressUtil.convert2SocketAddress(extraMessage.getTargetServerAddress()));
                return;
            }
            // 添加好友请求， 拒绝好友请求直接转发消息，只有同意好友请求才会绑定好友关系
            // 如果是好友申请，直接转发给对方各个端，不做消息保存；如果A和B同时添加好友，同时同意，则只会保留一份关系
            if (MessageContentEnum.FRIEND_AGREE.type() == message.getContentType()) {
                // 绑定好友关系
                DbHelper.bindFriend(from, to);
            }
            // 转发给该好友的各个设备端
            // 获取该客户端在线的所有客户端，进行推送消息已读
            List<LoginUserInfo> toLoginUserInfos = UserHelper.onlineAll(to);
            if (CollectionUtil.isEmpty(toLoginUserInfos)) {
                // 存入离线消息
                DbHelper.addOfflineMessage(to, packet);
                return;
            }
            MessageHelper.send2MultiDevices(packet, toLoginUserInfos);
        });
    }
}
