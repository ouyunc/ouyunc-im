package com.ouyunc.message.validator;

import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.AppStatus;
import com.ouyunc.domain.entity.AppEntity;
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
     * <p>Redis Hash miss / 断连时由 {@link DefaultRepository#getAppEntity(String)} 回源 {@code ouyunc_im_app}。</p>
     */
    @Override
    public boolean verify(String appKey, ChannelHandlerContext ctx) {
        AppEntity app;
        try {
            app = DefaultRepository.INSTANCE.getAppEntity(appKey);
        } catch (Exception e) {
            log.error("校验 appKey:{} 时查询异常", appKey, e);
            return false;
        }
        if (app == null) {
            log.warn("appKey:{}不存在", appKey);
            return false;
        }
        if (AppStatus.ABNORMAL.value().equals(app.getStatus())) {
            log.warn("appKey:{}已停用", appKey);
            return false;
        }
        Long maxConnections = app.getMaxConnections();
        // maxConnections == null 视为无限制（兼容未配置场景）
        if (maxConnections == null || maxConnections == NumberConstant.NUMBER_NEGATIVE_1) {
            return true;
        }
        long currentConnections;
        try {
            currentConnections = ClientHelper.connections(appKey);
        } catch (Exception e) {
            // Redis 断连时无法读连接数：已确认 app 合法，放行以免登录被 Redis 异常打断
            log.error("读取 appKey:{} 连接数失败，暂按未达上限处理", appKey, e);
            return true;
        }
        if (currentConnections < maxConnections) {
            return true;
        }
        log.warn("appKey:{}连接数已达上限, 当前:{}, 上限:{}", appKey, currentConnections, maxConnections);
        return false;
    }
}
