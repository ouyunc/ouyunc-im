package com.ouyunc.message.validator;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.AppStatus;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.domain.entity.AppEntity;
import com.ouyunc.message.cluster.lease.AppKeyConnQuotaSupport;
import com.ouyunc.message.cluster.lease.LocalNodeConnCounter;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fzx
 * @description appKey验证器断言,单例
 */
public enum AppKeyValidator implements Validator<String> {
    INSTANCE;
    private static final Logger log = LoggerFactory.getLogger(AppKeyValidator.class);

    /***
     * @author fzx
     * @description 校验appKey是否合法, 返回true -合法， 返回false-不合法
     *
     * <p>仅做存在性/状态/只读配额检查（HTTP）。WS 握手与登录请用 {@link #tryReserveForLogin} 原子预占。</p>
     * <p>Redis Hash miss / 断连时由 {@link DefaultRepository#getAppEntity(String)} 回源 {@code ouyunc_im_app}。</p>
     */
    @Override
    public boolean verify(String appKey, ChannelHandlerContext ctx) {
        AppEntity app = loadActiveApp(appKey);
        if (app == null) {
            return false;
        }
        return checkConnectionsReadonly(appKey, app.getMaxConnections());
    }

    /**
     * 登录路径原子预占：先 Lua 集群求和再 HINCRBY，再记本机计数。
     * 成功则打 {@link MessageConstant#CHANNEL_ATTR_KEY_CONN_QUOTA_RESERVED}，
     * {@link com.ouyunc.message.helper.ClientHelper#registerLocal} 不再二次 INCR。
     */
    public boolean tryReserveForLogin(String appKey, ChannelHandlerContext ctx) {
        if (ctx != null && Boolean.TRUE.equals(ChannelAttrUtil.getChannelAttribute(
                ctx, MessageConstant.CHANNEL_ATTR_KEY_CONN_QUOTA_RESERVED))) {
            String reservedAppKey = ChannelAttrUtil.getChannelAttribute(
                    ctx, MessageConstant.CHANNEL_ATTR_KEY_CONN_QUOTA_APP_KEY);
            if (appKey != null && appKey.equals(reservedAppKey)) {
                return true;
            }
            log.warn("连接配额已按 appKey={} 预占，拒绝改用 {}", reservedAppKey, appKey);
            return false;
        }
        AppEntity app = loadActiveApp(appKey);
        if (app == null) {
            return false;
        }
        Long maxConnections = app.getMaxConnections();
        if (maxConnections == null || maxConnections == NumberConstant.NUMBER_NEGATIVE_1) {
            return true;
        }
        try {
            if (!AppKeyConnQuotaSupport.tryReserve(appKey, maxConnections)) {
                log.warn("appKey:{}连接数已达上限, max={}", appKey, maxConnections);
                return false;
            }
        } catch (Exception e) {
            log.error("预占 appKey:{} 连接配额失败，拒绝登录", appKey, e);
            return false;
        }
        LocalNodeConnCounter.increment(appKey);
        if (ctx != null) {
            ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_CONN_QUOTA_RESERVED, Boolean.TRUE);
            ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_CONN_QUOTA_APP_KEY, appKey);
            hookReleaseOnClose(ctx.channel());
        }
        return true;
    }

    /**
     * 登录失败或未进入 registerLocal 时释放预占。
     */
    public static void releaseReservedIfNeeded(String appKey, ChannelHandlerContext ctx) {
        if (ctx == null) {
            return;
        }
        releaseReservedIfNeeded(appKey, ctx.channel());
    }

    public static void releaseReservedIfNeeded(String appKey, Channel channel) {
        if (channel == null || Boolean.TRUE != ChannelAttrUtil.getChannelAttribute(
                channel, MessageConstant.CHANNEL_ATTR_KEY_CONN_QUOTA_RESERVED)) {
            return;
        }
        ChannelAttrUtil.setChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_CONN_QUOTA_RESERVED, null);
        String reservedAppKey = appKey;
        if (reservedAppKey == null || reservedAppKey.isBlank()) {
            reservedAppKey = ChannelAttrUtil.getChannelAttribute(
                    channel, MessageConstant.CHANNEL_ATTR_KEY_CONN_QUOTA_APP_KEY);
        }
        ChannelAttrUtil.setChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_CONN_QUOTA_APP_KEY, null);
        if (reservedAppKey != null && !reservedAppKey.isBlank()) {
            LocalNodeConnCounter.decrement(reservedAppKey);
            // closeFuture 在 EventLoop 上；登录失败路径已在业务线程，直接释放即可。
            if (channel.eventLoop().inEventLoop()) {
                AppKeyConnQuotaSupport.releaseAsync(reservedAppKey);
            } else {
                AppKeyConnQuotaSupport.release(reservedAppKey);
            }
        }
    }

    private static void hookReleaseOnClose(Channel channel) {
        if (channel == null) {
            return;
        }
        channel.closeFuture().addListener(future -> releaseReservedIfNeeded(null, channel));
    }

    private AppEntity loadActiveApp(String appKey) {
        AppEntity app;
        try {
            app = DefaultRepository.INSTANCE.getAppEntity(appKey);
        } catch (Exception e) {
            log.error("校验 appKey:{} 时查询异常", appKey, e);
            return null;
        }
        if (app == null) {
            log.warn("appKey:{}不存在", appKey);
            return null;
        }
        if (AppStatus.ABNORMAL.value().equals(app.getStatus())) {
            log.warn("appKey:{}已停用", appKey);
            return null;
        }
        return app;
    }

    private boolean checkConnectionsReadonly(String appKey, Long maxConnections) {
        if (maxConnections == null || maxConnections == NumberConstant.NUMBER_NEGATIVE_1) {
            return true;
        }
        long currentConnections;
        try {
            currentConnections = AppKeyConnQuotaSupport.current(appKey);
        } catch (Exception e) {
            log.error("读取 appKey:{} 连接数失败，拒绝", appKey, e);
            return false;
        }
        if (currentConnections < maxConnections) {
            return true;
        }
        log.warn("appKey:{}连接数已达上限, 当前:{}, 上限:{}", appKey, currentConnections, maxConnections);
        return false;
    }
}
