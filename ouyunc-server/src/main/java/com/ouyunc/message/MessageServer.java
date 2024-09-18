package com.ouyunc.message;

/**
 * @author fzx
 * @description 消息服务器
 */
public interface MessageServer {


    /**
     * @Author fzx
     * @Description 启动服务端
     */
    void start(String[] args);


    /**
     * @Author fzx
     * @Description 停止服务端
     */
    void stop();
}
