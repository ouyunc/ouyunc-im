package com.ouyunc.message.validator;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.cache.config.CacheFactory;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * @author fzx
 * @description 是否存在好友请求校验
 */
public enum FriendRequestValidator implements Validator<Packet> {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(FriendRequestValidator.class);

    private static final RedisTemplate<String, Integer> redisTemplate = CacheFactory.REDIS.instance();

    /***
     * @author fzx
     * @description 校验是否存在有效的好友请求，如果存在返回true， 否则返回false
     */
    @Override
    public boolean verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        // 正在处理中的状态，如果为空 则说明没有正在处理中的好友请求，返回false, 如果有值（值为1-同意，2-拒绝），则返回true
        Integer friendRequestProcessing = redisTemplate.opsForValue().get(CacheConstant.OUYUNC + CacheConstant.APP_KEY + message.getMetadata().getAppKey() + CacheConstant.COLON + CacheConstant.FRIEND_REQUEST_PROCESSING + message.getFrom() + CacheConstant.COLON + message.getTo());
        if (null == friendRequestProcessing || friendRequestProcessing != MessageConstant.FRIEND_REQUEST_STATUS_JOINING) {
            log.error("不存在加好友请求记录或存在正在处理的好友请求，该消息忽略");
            return false;
        }
        return true;
    }
}
