package com.ouyunc.im;

/**
 * @Author fangzhenxun
 * @Description: 偶遇im的启动类总入口
 **/
public class StartIMServer {

    public static void main(String[] args) {
        IMServer imServer = new StandardIMServer();
        imServer.start(args);
    }


}
