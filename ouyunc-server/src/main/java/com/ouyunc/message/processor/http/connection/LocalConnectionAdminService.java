package com.ouyunc.message.processor.http.connection;

import com.ouyunc.base.constant.HttpRequestConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.OnlineEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本节点连接管理服务。
 * <p>完整登录上下文只驻留实际承载连接的节点，因此查询和强制下线都以本地注册表为权威。
 * 快照遍历允许弱一致：并发登录或退出最多反映在下一次查询，不持有全局锁阻塞登录链路。</p>
 */
public final class LocalConnectionAdminService {

    private static final Comparator<ConnectionEntry> CONNECTION_ORDER = Comparator
            .comparing((ConnectionEntry entry) -> entry.login().getAppKey(), Comparator.nullsFirst(String::compareTo))
            .thenComparing(entry -> entry.login().getIdentity(), Comparator.nullsFirst(String::compareTo))
            .thenComparingInt(entry -> entry.login().getDeviceType())
            .thenComparing(entry -> entry.channel().id().asLongText());

    private LocalConnectionAdminService() {
    }

    /** 按 appKey 查询当前节点有效连接；appKey 为空时查询全部租户。 */
    public static AdminConnectionQueryResponse query(String appKey, long requestedCurrent, long requestedSize) {
        long current = Math.max(1L, requestedCurrent);
        long size = Math.min(Math.max(1L, requestedSize),
                HttpRequestConstant.HTTP_ADMIN_CONNECTION_PAGE_SIZE_MAX);
        List<ConnectionEntry> entries = snapshot(appKey);
        entries.sort(CONNECTION_ORDER);

        Map<String, Long> countByAppKey = new LinkedHashMap<>();
        for (ConnectionEntry entry : entries) {
            countByAppKey.merge(entry.login().getAppKey(), 1L, Long::sum);
        }
        List<AppKeyConnectionCount> counts = countByAppKey.entrySet().stream()
                .map(entry -> new AppKeyConnectionCount(entry.getKey(), entry.getValue()))
                .toList();

        long fromLong;
        try {
            fromLong = Math.multiplyExact(current - 1L, size);
        } catch (ArithmeticException ignored) {
            fromLong = Long.MAX_VALUE;
        }
        int from = fromLong >= entries.size() ? entries.size() : (int) fromLong;
        int to = (int) Math.min(entries.size(), (long) from + size);
        List<AdminConnectionInfo> details = entries.subList(from, to).stream()
                .map(LocalConnectionAdminService::toInfo)
                .toList();
        long total = entries.size();
        long pages = total == 0L ? 0L : (total + size - 1L) / size;
        return new AdminConnectionQueryResponse(
                AdminConnectionQueryResponse.SCOPE_LOCAL_NODE,
                localNode(),
                System.currentTimeMillis(),
                counts,
                details,
                total,
                size,
                current,
                pages);
    }

    /**
     * 强制关闭匹配连接。只关闭快照时仍由同一注册表 key 指向的 Channel，避免并发重登录时误关新会话。
     */
    public static AdminConnectionOfflineResponse offline(AdminConnectionOfflineRequest request) {
        List<ConnectionEntry> entries = snapshot(request.getAppKey());
        int matched = 0;
        int closeRequested = 0;
        int skippedInactive = 0;
        for (ConnectionEntry entry : entries) {
            if (!matches(request, entry)) {
                continue;
            }
            matched++;
            ChannelHandlerContext current = MessageServerContext.localLoginClientRegisterTable.get(entry.registryKey());
            Channel channel = entry.channel();
            if (current != entry.context() || !channel.isActive()) {
                skippedInactive++;
                continue;
            }
            channel.close();
            closeRequested++;
        }
        return new AdminConnectionOfflineResponse(
                AdminConnectionQueryResponse.SCOPE_LOCAL_NODE,
                localNode(), matched, closeRequested, skippedInactive);
    }

    /** 空条件禁止执行；全节点下线必须由调用方显式表达。 */
    public static boolean hasEffectiveOfflineCondition(AdminConnectionOfflineRequest request) {
        return request != null && (request.isAll()
                || StringUtils.isNotBlank(request.getAppKey())
                || StringUtils.isNotBlank(request.getIdentity())
                || StringUtils.isNotBlank(request.getChannelId()));
    }

    /** {@code all=true} 只能表示全节点下线，拒绝与过滤条件混用产生歧义。 */
    public static boolean hasConflictingAllCondition(AdminConnectionOfflineRequest request) {
        return request != null && request.isAll()
                && (StringUtils.isNotBlank(request.getAppKey())
                || StringUtils.isNotBlank(request.getIdentity())
                || request.getDeviceType() != null
                || StringUtils.isNotBlank(request.getChannelId()));
    }

    private static List<ConnectionEntry> snapshot(String appKey) {
        List<ConnectionEntry> result = new ArrayList<>();
        for (Map.Entry<String, ChannelHandlerContext> entry
                : MessageServerContext.localLoginClientRegisterTable.asMap().entrySet()) {
            ChannelHandlerContext context = entry.getValue();
            if (context == null || context.channel() == null || !context.channel().isActive()) {
                continue;
            }
            LoginClientInfo login = ChannelAttrUtil.getChannelAttribute(
                    context.channel(), MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            if (login == null || !OnlineEnum.ONLINE.equals(login.getOnlineStatus())) {
                continue;
            }
            if (StringUtils.isNotBlank(appKey) && !appKey.equals(login.getAppKey())) {
                continue;
            }
            result.add(new ConnectionEntry(entry.getKey(), context, context.channel(), login));
        }
        return result;
    }

    private static boolean matches(AdminConnectionOfflineRequest request, ConnectionEntry entry) {
        if (request.isAll()) {
            return true;
        }
        LoginClientInfo login = entry.login();
        return (StringUtils.isBlank(request.getAppKey()) || request.getAppKey().equals(login.getAppKey()))
                && (StringUtils.isBlank(request.getIdentity()) || request.getIdentity().equals(login.getIdentity()))
                && (request.getDeviceType() == null || request.getDeviceType() == login.getDeviceType())
                && (StringUtils.isBlank(request.getChannelId())
                    || request.getChannelId().equals(entry.channel().id().asLongText())
                    || request.getChannelId().equals(entry.channel().id().asShortText()));
    }

    private static AdminConnectionInfo toInfo(ConnectionEntry entry) {
        LoginClientInfo login = entry.login();
        Channel channel = entry.channel();
        String realIp = ChannelAttrUtil.getChannelAttribute(
                channel, MessageConstant.CHANNEL_ATTR_KEY_TAG_CLIENT_REAL_IP);
        return new AdminConnectionInfo(
                channel.id().asLongText(),
                login.getAppKey(),
                login.getIdentity(),
                login.getDeviceType(),
                localNode(),
                login.getNodeEpoch(),
                login.getLastLoginTime(),
                login.getCreateTime(),
                realIp,
                addressText(channel.remoteAddress()),
                addressText(channel.localAddress()),
                login.getProtocol(),
                login.getProtocolVersion(),
                login.getScope(),
                login.getHeartBeatTimeout(),
                channel.isActive(),
                channel.isWritable());
    }

    private static String localNode() {
        return MessageServerContext.serverProperties().getLocalServerAddress();
    }

    private static String addressText(Object address) {
        return address == null ? null : address.toString();
    }

    private record ConnectionEntry(String registryKey, ChannelHandlerContext context,
                                   Channel channel, LoginClientInfo login) {
    }
}
