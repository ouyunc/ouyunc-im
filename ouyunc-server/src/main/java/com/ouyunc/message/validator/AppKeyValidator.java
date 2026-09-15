package com.ouyunc.message.validator;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.AppStatus;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.domain.entity.AppEntity;
import com.ouyunc.message.cluster.lease.LocalNodeConnCounter;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.repository.DefaultRepository;
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
     * <p>仅做存在性/状态/只读配额检查（握手、HTTP）。登录请用 {@link #tryReserveForLogin} 原子预占。</p>
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
     * B5：登录路径原子预占本机连接额度；成功则打 {@link MessageConstant#CHANNEL_ATTR_KEY_CONN_QUOTA_RESERVED}，
     * {@link com.ouyunc.message.helper.ClientHelper#registerLocal} 不再二次 INCR。
     */
    public boolean tryReserveForLogin(String appKey, ChannelHandlerContext ctx) {
        AppEntity app = loadActiveApp(appKey);
        if (app == null) {
            return false;
        }
        Long maxConnections = app.getMaxConnections();
        if (maxConnections == null || maxConnections == NumberConstant.NUMBER_NEGATIVE_1) {
            return true;
        }
        long remoteOthers;
        try {
            long total = ClientHelper.connections(appKey);
            long local = LocalNodeConnCounter.get(appKey);
            remoteOthers = Math.max(0L, total - local);
        } catch (Exception e) {
            log.error("读取 appKey:{} 连接数失败，拒绝登录预占", appKey, e);
            return false;
        }
        long roomForLocal = maxConnections - remoteOthers;
        if (roomForLocal <= 0L) {
            log.warn("appKey:{}连接数已达上限(含远端), max={}, remoteOthers={}", appKey, maxConnections, remoteOthers);
            return false;
        }
        if (!LocalNodeConnCounter.tryIncrementIfBelow(appKey, roomForLocal)) {
            log.warn("appKey:{}本机连接预占失败, maxLocalRoom={}, local={}",
                    appKey, roomForLocal, LocalNodeConnCounter.get(appKey));
            return false;
        }
        if (ctx != null) {
            ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_CONN_QUOTA_RESERVED, Boolean.TRUE);
        }
        return true;
    }

    /**
     * 登录失败或未进入 registerLocal 时释放预占。
     */
    public static void releaseReservedIfNeeded(String appKey, ChannelHandlerContext ctx) {
        if (ctx == null || Boolean.TRUE != ChannelAttrUtil.getChannelAttribute(
                ctx, MessageConstant.CHANNEL_ATTR_KEY_CONN_QUOTA_RESERVED)) {
            return;
        }
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_CONN_QUOTA_RESERVED, null);
        LocalNodeConnCounter.decrement(appKey);
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
            currentConnections = ClientHelper.connections(appKey);
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
