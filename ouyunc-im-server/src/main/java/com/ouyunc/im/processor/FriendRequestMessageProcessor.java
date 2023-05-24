package com.ouyunc.im.processor;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.SystemClock;
import cn.hutool.json.JSONUtil;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.InnerExtraData;
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
            InnerExtraData innerExtraData = JSONUtil.toBean(message.getExtra(), InnerExtraData.class);
            if (innerExtraData == null) {
                innerExtraData = new InnerExtraData();
            }
            // 下面是对集群以及qos消息可靠进行处理
            // 根据to从分布式缓存中取出targetServerAddress目标地址
            String to = message.getTo();
            // 判断是否从其他服务路由过来的额消息
            if (innerExtraData.isDelivery()) {
                if (IMServerContext.SERVER_CONFIG.getLocalServerAddress().equals(innerExtraData.getTargetServerAddress()) || !IMServerContext.SERVER_CONFIG.isClusterEnable()) {
                    MessageHelper.sendMessage(packet, IdentityUtil.generalComboIdentity(to, innerExtraData.getDeviceEnum().getName()));
                    return;
                }
                MessageHelper.deliveryMessage(packet, SocketAddressUtil.convert2SocketAddress(innerExtraData.getTargetServerAddress()));
                return;
            }
            // 存到缓存中7天，@todo做到可配置
            DbHelper.handleFriendRequest(packet);
            // 转发给该好友的各个设备端
            // 获取该客户端在线的所有客户端，进行推送消息已读
            List<LoginUserInfo> toLoginUserInfos = UserHelper.onlineAll(to);
            if (CollectionUtil.isEmpty(toLoginUserInfos)) {
                // 存入离线消息
                DbHelper.write2OfflineTimeline(packet, to, SystemClock.now());
                return;
            }
            MessageHelper.send2MultiDevices(packet, toLoginUserInfos);
        });
    }
}
