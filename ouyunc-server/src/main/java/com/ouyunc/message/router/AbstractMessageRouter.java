package com.ouyunc.message.router;

import com.ouyunc.base.packet.Packet;

import javax.annotation.Nullable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author fzx
 * @description 消息路由器
 */
public abstract class AbstractMessageRouter implements Router<String, Packet, String>{

    /***
     * 消息路由器的虚拟线程池
     */
    public static final ExecutorService routerExecutor = Executors.newVirtualThreadPerTaskExecutor();


    /***
     * @author fzx
     * @description 路由,查找出符合条件的服务地址
     */
    @Nullable
    public abstract String route(Packet packet, String currentRoutedServerAddress);

}
