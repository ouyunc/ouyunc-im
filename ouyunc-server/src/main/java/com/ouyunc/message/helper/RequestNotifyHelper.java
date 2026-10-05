package com.ouyunc.message.helper;

import com.google.common.collect.Sets;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 关系请求（好友/群）WS 推送的通用基础设施。
 */
public final class RequestNotifyHelper {

    private RequestNotifyHelper() {
    }

    /**
     * 在请求完成器的工作线程查询在线状态并提交投递。失败向上传播，不能提前写 delivery-done。
     * MessageDeliveryPlanner 内部负责线程切换，不依赖发起请求的旧 Channel 是否仍在线。
     */
    public static void dispatchCommitted(Packet packet, String appKey, Collection<String> identities) {
        if (CollectionUtils.isEmpty(identities)) {
            return;
        }
        List<LoginClientInfo> clients = new ArrayList<>();
        for (String identity : identities) {
            if (StringUtils.isNotBlank(identity)) {
                List<LoginClientInfo> online = ClientHelper.onlineAll(appKey, identity);
                if (CollectionUtils.isNotEmpty(online)) {
                    clients.addAll(online);
                }
            }
        }
        if (!clients.isEmpty()) {
            MessageDeliveryPlanner.deliverOnlineClients(packet, clients);
        }
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

}
