package com.ouyu.im;

/**
 * @Author fangzhenxun
 * @Description: 偶遇im的启动类总入口
 * @Version V1.0
 **/
public class StartIMServer {
    public static void main(String[] args) {
        IMServer imServer = new StandardIMServer();
        new Thread(()-> {
            imServer.start();
        }).start();
    }
}
