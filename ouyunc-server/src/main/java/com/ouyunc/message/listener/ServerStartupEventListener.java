package com.ouyunc.message.listener;

import com.ouyunc.base.constant.enums.SaveModeEnum;
import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.ServerStartupEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.thread.ClientLoginKeepAliveThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fzx
 * @description 服务启动成功事件
 */
public class ServerStartupEventListener implements MessageListener<ServerStartupEvent> {
    private static final Logger log = LoggerFactory.getLogger(ServerStartupEventListener.class);

    /**
     * 服务启动成功事件,
     * @param event
     */
    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        // 判断是否开启客户端登录信息的
        if (MessageServerContext.serverProperties().isClientHeartBeatEnable() && SaveModeEnum.FINITE.equals(MessageServerContext.serverProperties().getClientLoginInfoSaveMode())) {
            Thread clientLoginKeepAliveThread = new Thread(new ClientLoginKeepAliveThread());
            // 设置线程参数
            clientLoginKeepAliveThread.setName("client-login-keep-alive-thread");
            clientLoginKeepAliveThread.setDaemon(true);
            // 开启线程
            clientLoginKeepAliveThread.start();
        }
    }
}
