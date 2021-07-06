package com.ouyu.im;

/**
 * @Author fangzhenxun
 * @Description: 将im服务的基本操作统一抽象成接口
 * @Version V1.0
 **/
public interface IMServer {


    /**
     * @Author fangzhenxun
     * @Description 启动im服务端
     * @param
     * @return void
     */
    void start() ;


    /**
     * @Author fangzhenxun
     * @Description 停止im服务端
     * @return void
     */
    void stop();

}
