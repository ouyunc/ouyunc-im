package com.ouyunc.im;

/**
 * @Author fangzhenxun
 * @Description: 将im服务的基本操作统一抽象成接口
 **/
public interface IMServer {


    /**
     * @Author fangzhenxun
     * @Description 启动im服务端
     * @return void
     */
    void start(String[] args) ;


    /**
     * @Author fangzhenxun
     * @Description 停止im服务端
     * @return void
     */
    void stop();

}
