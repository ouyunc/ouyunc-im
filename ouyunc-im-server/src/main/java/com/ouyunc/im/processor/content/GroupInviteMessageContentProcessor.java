package com.ouyunc.im.processor.content;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.GroupRequestContent;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 群成员邀请他人加入群
 */
public class GroupInviteMessageContentProcessor  extends AbstractMessageContentProcessor{
    private static Logger log = LoggerFactory.getLogger(GroupInviteMessageContentProcessor.class);

    @Override
    public MessageContentEnum messageContentType() {
        return MessageContentEnum.GROUP_INVITE_JOIN;
    }

    /**
     * 处理群成员邀请他人加入该群（有可能被邀请人已经是该群成员，做最大兼容性）
     * @param ctx
     * @param packet
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("GroupInviteMessageContentProcessor 正在处理邀请加群请求 packet: {}...", packet);
        Message message = (Message) packet.getMessage();
        GroupRequestContent groupRequestContent = JSONUtil.toBean(message.getContent(), GroupRequestContent.class);
        JSONArray inviteUserIds = JSONUtil.parseArray(groupRequestContent.getData());
        log.info("end");
    }
}
