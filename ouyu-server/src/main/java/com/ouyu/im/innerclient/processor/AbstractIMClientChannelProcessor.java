package com.ouyu.im.innerclient.processor;

/**
 * @Author fangzhenxun
 * @Description: 抽象内置客户端通道处理器
 * @Version V1.0
 **/
public abstract class AbstractIMClientChannelProcessor implements IMClientChannelProcessor, Runnable{


    /**
     * @Author fangzhenxun
     * @Description 最终的业务逻辑会交给process 来处理
     * @param
     * @return void
     */
    @Override
    public void run() {
        this.process();
    }
}
