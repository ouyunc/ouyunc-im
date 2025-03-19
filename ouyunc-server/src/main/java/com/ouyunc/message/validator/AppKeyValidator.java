package com.ouyunc.message.validator;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.domain.constants.AppStatus;
import com.ouyunc.domain.entity.AppEntity;
import com.ouyunc.message.helper.ClientHelper;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;

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
     */
    @Override
    public boolean verify(String appKey, ChannelHandlerContext ctx) {
        RedisTemplate<String, String> redisTemplate = CacheFactory.REDIS.instance();
        Map<String, AppEntity> appKeys = redisTemplate.<String, AppEntity>opsForHash().entries(CacheConstant.OUYUNC + CacheConstant.APP_KEYS);
        if (MapUtils.isEmpty(appKeys) || !appKeys.containsKey(appKey)) {
            log.error("appKey:{}不存在", appKey);
            return false;
        }
        // 获取appKey的设置信息，是否停用等
        AppEntity app = appKeys.get(appKey);
        if (app == null || AppStatus.ABNORMAL.value().equals(app.getStatus())) {
            log.error("appKey:{}已停用", appKey);
            return false;
        }
        // 获取当前appKey的连接数
        long currentConnections = ClientHelper.connections(appKey);
        Long maxConnections = app.getMaxConnections();
        if (maxConnections != null && (maxConnections == NumberConstant.NUMBER_NEGATIVE_1 || currentConnections < maxConnections)) {
            return true;
        }
        log.error("appKey:{}连接数已达上限", appKey);
        return false;
    }
}
