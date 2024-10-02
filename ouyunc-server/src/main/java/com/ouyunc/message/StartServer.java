package com.ouyunc.message;

import java.io.IOException;

/**
 * @Author fzx
 * @Description: 启动类总入口
 **/
public class StartServer {

    public static void main(String[] args) throws IOException {
        MessageServer server = new StandardMessageServer();
        server.start(args);

    }
}
