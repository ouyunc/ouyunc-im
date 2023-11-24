package com.ouyunc.im.processor;

import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.domain.bo.ImGroupUserBO;
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
public class WithdrawMessageProcessor extends AbstractMessageProcessor {
    private static Logger log = LoggerFactory.getLogger(WithdrawMessageProcessor.class);

    @Override
    public MessageEnum messageType() {
        return MessageEnum.IM_WITHDRAW;
    }

    /**
     * 做具体的撤回逻辑
     *
     * @param ctx
     * @param packet
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("WithdrawMessageProcessor 撤回处理器正在处理消息：{}", packet);
        fireProcess(ctx, packet, (ctx0, packet0) -> {
            Message message = (Message) packet.getMessage();
            int contentType = message.getContentType();
            // 目前只针对私聊和群聊消息进行撤回
            if (MessageContentEnum.PRIVATE_CHAT_WITHDRAW_CONTENT.type() != contentType && MessageContentEnum.GROUP_OR_CUSTOMER_CHAT_WITHDRAW_CONTENT.type() != contentType) {
                return;
            }
            // 下面是对集群以及qos消息可靠进行处理
            String from = message.getFrom();
            // 根据to从分布式缓存中取出targetServerAddress目标地址
            String to = message.getTo();
            // 将消息写到发件箱和及接收方的收件箱
            long timestamp = SystemClock.now();
            DbHelper.write2SendTimeline(packet, from, timestamp);
            DbHelper.write2ReceiveTimeline(packet, to, timestamp);
            // 修改发件箱和收件箱的消息状态,离线消息，和缓存不做处理交给客户端处理（为了提高服务端性能，能让客户端做的，尽量让客户端去处理）
            DbHelper.handleWithdrawMessage(packet);
            // 发送给自己的其他端
            List<LoginUserInfo> fromLoginUserInfos = UserHelper.onlineAll(from, packet.getDeviceType());
            // 排除自己，发给其他端
            // 转发给自己客户端的各个设备端
            MessageHelper.send2MultiDevices(packet, fromLoginUserInfos);
            // 获取该客户端在线的所有客户端，进行推送消息已读
            if (MessageContentEnum.PRIVATE_CHAT_WITHDRAW_CONTENT.type() == contentType) {
                List<LoginUserInfo> toLoginUserInfos = UserHelper.onlineAll(to);
                if (CollectionUtils.isEmpty(toLoginUserInfos)) {
                    // 存入离线消息，不以设备来区分
                    DbHelper.write2OfflineTimeline(packet, to, timestamp);
                    return;
                }
                // 转发给某个客户端的各个设备端
                MessageHelper.send2MultiDevices(packet, toLoginUserInfos);
            }
            if (MessageContentEnum.GROUP_OR_CUSTOMER_CHAT_WITHDRAW_CONTENT.type() == contentType) {
                // 首先从缓存中获取群成员(包括自身)，如果没有在从数据库获取
                List<ImGroupUserBO> groupMembers = DbHelper.getGroupMembers(to);
                // 循环遍历
                if (CollectionUtils.isEmpty(groupMembers)) {
                    // 解散了
                    return;
                }
                // 遍历所有的群成员
                for (ImGroupUserBO groupMember : groupMembers) {
                    // 目前使用id号来作为唯一标识
                    if (!from.equals(groupMember.getUserId()) && IMConstant.NOT_SHIELD.equals(groupMember.getIsShield())) {
                        // 群里其它人员的其他端
                        // 判断，群成员是否屏蔽了该群，如果屏蔽则不能接受到该消息
                        List<LoginUserInfo> othersMembersLoginUserInfos = UserHelper.onlineAll(groupMember.getUserId());
                        if (CollectionUtils.isEmpty(othersMembersLoginUserInfos)) {
                            // 存入离线消息
                            DbHelper.write2OfflineTimeline(packet, groupMember.getUserId(), timestamp);
                        } else {
                            // 转发给某个客户端的各个设备端
                            MessageHelper.send2MultiDevices(packet, othersMembersLoginUserInfos);
                        }

                    }
                }
            }

        });
    }
}
