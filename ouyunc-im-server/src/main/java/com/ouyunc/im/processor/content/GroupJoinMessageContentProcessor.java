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
import io.netty.channel.ChannelHandlerContext;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 加群
 */
public class GroupJoinMessageContentProcessor extends AbstractMessageContentProcessor{
    private static Logger log = LoggerFactory.getLogger(GroupJoinMessageContentProcessor.class);

    @Override
    public MessageContentEnum messageContentType() {
        return MessageContentEnum.GROUP_JOIN;
    }

    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("GroupJoinMessageContentProcessor 正在处理加群请求 packet: {}...", packet);
        Message message = (Message) packet.getMessage();
        // 根据to从分布式缓存中取出targetServerAddress目标地址
        String to = message.getTo();
        GroupRequestContent groupRequestContent = JSONUtil.toBean(message.getContent(), GroupRequestContent.class);
        // 处理群组请求
        RLock lock = RedissonFactory.INSTANCE.redissonClient().getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.GROUP + CacheConstant.REFUSE_AGREE + IdentityUtil.sortComboIdentity(groupRequestContent.getIdentity(), groupRequestContent.getGroupId()));
        try{
            lock.lock();
            DbHelper.handleGroupRequest(packet);
        }finally {
            lock.unlock();
        }
        // 查找群中的管理员以及群主，向其投递加群的请求
        List<ImGroupUserBO> groupManagerMembers = DbHelper.getGroupMembers(to, true);
        if (CollectionUtil.isEmpty(groupManagerMembers)) {
            return;
        }
        for (ImGroupUserBO groupManagerMember : groupManagerMembers) {
            // 判断该管理员是否在线，如果不在线放入离线消息
            List<LoginUserInfo> managersLoginUserInfos = UserHelper.onlineAll(groupManagerMember.getUserId());
            if (CollectionUtil.isEmpty(managersLoginUserInfos)) {
                // 存入离线消息
                DbHelper.write2OfflineTimeline(packet, groupManagerMember.getUserId(), SystemClock.now());
            }else {
                // 转发给某个客户端的各个设备端
                MessageHelper.send2MultiDevices(packet, managersLoginUserInfos);
            }

        }
    }
}
