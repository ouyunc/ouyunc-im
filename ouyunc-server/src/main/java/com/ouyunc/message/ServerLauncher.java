package com.ouyunc.message;

import com.ouyunc.config.ConfigBootstrap;

import java.io.IOException;

/**
 * @Author fzx
 * @Description: 启动类总入口
 **/
public class ServerLauncher {

    public static void main(String[] args) throws IOException, InterruptedException {
        // 唯一配置入口：合并 classpath、外部文件、配置中心，并绑定 Redis / MQ / 数据库
        ConfigBootstrap.load(args);
        MessageServer server = new StandardMessageServer();
        server.start(args);
    }
}
