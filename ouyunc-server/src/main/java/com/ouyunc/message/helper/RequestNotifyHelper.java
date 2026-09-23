package com.ouyunc.message.helper;

import com.google.common.collect.Sets;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 关系请求（好友/群）WS 推送的通用基础设施。
 */
public final class RequestNotifyHelper {
    private static final Logger log = LoggerFactory.getLogger(RequestNotifyHelper.class);

    private RequestNotifyHelper() {
    }

    public static Set<String> userOnly(String userId) {
        Set<String> identities = new HashSet<>();
        if (StringUtils.isNotBlank(userId)) {
            identities.add(userId);
        }
        return identities;
    }

    public static Set<String> copyOf(Collection<String> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Sets.newHashSet();
        }
        return new HashSet<>(userIds);
    }

    public static Set<String> copyExcept(Collection<String> userIds, String excludedUserId) {
        Set<String> identities = copyOf(userIds);
        if (StringUtils.isNotBlank(excludedUserId)) {
            identities.remove(excludedUserId);
        }
        return identities;
    }

    public static Set<String> withUser(Collection<String> userIds, String additionalUserId) {
        Set<String> identities = copyOf(userIds);
        if (StringUtils.isNotBlank(additionalUserId)) {
            identities.add(additionalUserId);
        }
        return identities;
    }

    /**
     * 查在线离开 EventLoop；通知投递回到连接 EventLoop。发送方受理结果由请求处理器单独返回。
     */
    public static void dispatch(ChannelHandlerContext ctx, Packet packet, String appKey, Collection<String> identities) {
        if (CollectionUtils.isEmpty(identities)) {
            return;
        }
        Runnable lookupAndDispatch = () -> {
            try {
                List<LoginClientInfo> clients = new ArrayList<>();
                for (String identity : identities) {
                    if (StringUtils.isBlank(identity)) {
                        continue;
                    }
                    List<LoginClientInfo> online = ClientHelper.onlineAll(appKey, identity);
                    if (CollectionUtils.isNotEmpty(online)) {
                        clients.addAll(online);
                    }
                }
                Runnable onLoop = () -> {
                    if (CollectionUtils.isNotEmpty(clients)) {
                        MessageHelper.asyncSendMessage(packet, clients);
                    }
                };
                if (ctx.channel().eventLoop().inEventLoop()) {
                    onLoop.run();
                } else {
                    ctx.channel().eventLoop().execute(onLoop);
                }
            } catch (RuntimeException error) {
                // 请求已写入，在线通知失败不改变发送方的受理状态。
                log.error("关系请求已受理但在线通知失败, messageId={}", packet.getMessage().getId(), error);
            }
        };
        try {
            if (ctx.channel().eventLoop().inEventLoop()) {
                ThreadPoolManager.messageProcessorExecutor().execute(lookupAndDispatch);
            } else {
                lookupAndDispatch.run();
            }
        } catch (RuntimeException error) {
            log.error("关系请求已受理但通知任务提交失败, messageId={}", packet.getMessage().getId(), error);
        }
    }
}
