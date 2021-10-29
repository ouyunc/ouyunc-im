package com.ouyu.im.context;

import com.ouyu.im.config.IMServerConfig;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;

/**
 * @Author fangzhenxun
 * @Description: im 上线文
 * @Version V1.0
 **/
public class IMContext {


    /**
     * 本地服务地址 ${host}:${port}
     */
    public static String LOCAL_ADDRESS;


    /**
     * IM 服务配置文件
     */
    public static IMServerConfig SERVER_CONFIG;


    /**
     * IM 全局时间执行器
     */
    public static final EventExecutorGroup EVENT_EXECUTORS= new DefaultEventExecutorGroup(16);


}
