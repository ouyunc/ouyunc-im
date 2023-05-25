package com.ouyunc.im.processor.content;

import cn.hutool.json.JSONUtil;
import com.im.cache.l1.distributed.redis.redisson.RedissonFactory;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.GroupRequestContent;
import com.ouyunc.im.utils.IdentityUtil;
import io.netty.channel.ChannelHandlerContext;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 被邀请者拒绝邀请加入群
 */
public class GroupRefuseInviteMessageContentProcessor extends AbstractMessageContentProcessor{


    private static Logger log = LoggerFactory.getLogger(GroupRefuseInviteMessageContentProcessor.class);

    @Override
    public MessageContentEnum messageContentType() {
        return MessageContentEnum.GROUP_AGREE;
    }


    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("GroupRefuseInviteMessageContentProcessor 正在处理群邀请拒绝请求 packet: {}...", packet);
        DbHelper.handleGroupRefuseInviteRequest(packet);
        // 对群邀请人来讲并不太关心被邀请人同意不同意，所以这里就不进行消息通知邀请人了，和邀请的时候一致，也不保存邀请的信息
    }
}
