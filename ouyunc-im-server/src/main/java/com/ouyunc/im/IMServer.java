package com.ouyunc.im;

/**
 * @Author fangzhenxun
 * @Description: 将im服务的基本操作统一抽象成接口
 **/
public interface IMServer {


    /**
     * @return void
     * @Author fangzhenxun
     * @Description 启动im服务端
     */
    void start(String[] args);


    /**
     * @return void
     * @Author fangzhenxun
     * @Description 停止im服务端
     */
    void stop();

}
