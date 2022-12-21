package com.ouyunc.im;


import cn.hutool.core.collection.ConcurrentHashSet;
import com.ouyunc.im.context.IMServerContext;
import io.netty.channel.Channel;

/**
 * @Author fangzhenxun
 * @Description: 偶遇im的启动类总入口
 * @Version V3.0
 **/
public class StartIMServer {

    public static void main(String[] args) {

        String path = "com.ouyunc.im.StartIMServer.main(com.String, cn.xxx.User)";
        String[] split = path.split("()");

        IMServer imServer = new StandardIMServer();
        imServer.start();
    }


}
