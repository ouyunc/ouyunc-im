package com.ouyunc.im.processor;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.enums.MessageTypeEnum;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.InnerExtraData;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.utils.SystemClock;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 好友申请/拒绝/同意处理器；
 * 删除好友和黑名单通过http服务器来实现，这里不做处理
 */
public class FriendRequestMessageProcessor extends AbstractMessageProcessor {
    private static Logger log = LoggerFactory.getLogger(FriendRequestMessageProcessor.class);


    @Override
    public MessageTypeEnum messageType() {
        return MessageTypeEnum.IM_FRIEND_REQUEST;
    }

    /**
     * 逻辑处理
     *
     * @param ctx
     * @param packet
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("FriendRequestMessageProcessor 正在处理好友请求消息packet: {}", packet);
        fireProcess(ctx, packet, (ctx0, packet0) -> {
            Message message = (Message) packet.getMessage();
            ExtraMessage extraMessage = JSON.parseObject(message.getExtra(), ExtraMessage.class);
            InnerExtraData innerExtraData = extraMessage.getInnerExtraData();
            String appKey = innerExtraData.getAppKey();
            String to = message.getTo();
            // 存到缓存中7天
            long timestamp = SystemClock.now();
            DbHelper.handleFriendRequest(appKey, packet, timestamp);
            // 无论是否在线都先存入离线表
            DbHelper.write2OfflineTimeline(appKey, packet, to, timestamp);
            // 转发给该好友的各个设备端
            List<LoginUserInfo> toLoginUserInfos = UserHelper.onlineAll(appKey, to);
            if (CollectionUtils.isNotEmpty(toLoginUserInfos)) {
                MessageHelper.send2MultiDevices(packet, toLoginUserInfos);
            }
        });
    }
}
