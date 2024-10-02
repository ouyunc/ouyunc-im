package com.ouyunc.message.listener;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.ClientOnlineEvent;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 上线事件监听器
 **/
public class ClientOnlineListener implements MessageListener<ClientOnlineEvent> {

    private static final Logger log = LoggerFactory.getLogger(ClientOnlineListener.class);


    /**
     * @Author fzx
     * @Description 处理客户端上线事件/成功登录的时候会触发
     */
    @Override
    public void onApplicationEvent(ClientOnlineEvent event) {
        if (log.isDebugEnabled()) {
            log.debug("客户端上线事件监听器正在处理：{}", event);
        }
        if (MessageServerContext.serverProperties().isClientHeartBeatEnable()) {
            // 发布时间就是登录成功的时间，这里也将登录成功的时间记作首次服务端首次收到消息的时间，后面开启心跳后，每次到达心跳时间会将其进行更新，
            // 上次心跳时间戳
            // 将成功登录时间设置到ctx 中进行保存；
            event.getChannelHandlerContext().channel().attr(AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_TAG_LAST_HEARTBEAT_TIMESTAMP)).set(event.getPublishTime());
        }
    }
}
