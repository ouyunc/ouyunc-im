package com.ouyunc.message;

/**
 * @Author fzx
 * @Description: 启动类总入口
 **/
public class StartServer {

    public static void main(String[] args) {
        MessageServer server = new StandardMessageServer();
        server.start(args);
    }


}
