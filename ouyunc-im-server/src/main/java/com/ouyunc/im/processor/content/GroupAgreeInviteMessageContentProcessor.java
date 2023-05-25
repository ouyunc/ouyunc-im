package com.ouyunc.im.processor.content;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.SystemClock;
import cn.hutool.json.JSONUtil;
import com.im.cache.l1.distributed.redis.redisson.RedissonFactory;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.domain.bo.ImGroupUserBO;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.GroupRequestContent;
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.validate.MessageValidate;
import io.netty.channel.ChannelHandlerContext;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 被邀请者同意邀请加入
 */
public class GroupAgreeInviteMessageContentProcessor extends AbstractMessageContentProcessor{


    private static Logger log = LoggerFactory.getLogger(GroupAgreeInviteMessageContentProcessor.class);

    @Override
    public MessageContentEnum messageContentType() {
        return MessageContentEnum.GROUP_AGREE;
    }


    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("GroupAgreeInviteMessageContentProcessor 正在处理群邀请同意请求 packet: {}...", packet);
        Message message = (Message) packet.getMessage();
        GroupRequestContent groupRequestContent = JSONUtil.toBean(message.getContent(), GroupRequestContent.class);
        //获取锁
        RLock lock = RedissonFactory.INSTANCE.redissonClient().getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.GROUP + CacheConstant.REFUSE_AGREE + IdentityUtil.sortComboIdentity(groupRequestContent.getIdentity(), groupRequestContent.getGroupId()));
        try{
            lock.lock();
            // 处理群邀请同意请求
            DbHelper.handleGroupAgreeInviteRequest(packet);
        }finally {
            lock.unlock();
        }
        // 对群邀请人来讲并不太关心被邀请人同意不同意，所以这里就不进行消息通知邀请人了，和邀请的时候一致，也不保存邀请的信息
    }
}
