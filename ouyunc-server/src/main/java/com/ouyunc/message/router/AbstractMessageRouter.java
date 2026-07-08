package com.ouyunc.message.router;

import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.packet.Packet;

import javax.annotation.Nullable;
import java.util.concurrent.ExecutorService;

/**
 * @author fzx
 * @description 消息路由器
 */
public abstract class AbstractMessageRouter implements Router<String, Packet, String>{

    /***
     * 消息路由器的虚拟线程池
     */
    protected ExecutorService routerExecutor() {
        return ThreadPoolManager.routerExecutor();
    }


    /***
     * @author fzx
     * @description 路由,查找出符合条件的服务地址
     */
    @Nullable
    public abstract String route(Packet packet, String currentRoutedServerAddress);

}
