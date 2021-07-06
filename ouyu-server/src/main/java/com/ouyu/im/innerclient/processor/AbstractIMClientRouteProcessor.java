package com.ouyu.im.innerclient.processor;

/**
 * @Author fangzhenxun
 * @Description: 抽象 IMClientRoute 处理类
 * @Version V1.0
 **/
public abstract class AbstractIMClientRouteProcessor implements IMClientRouteProcessor, Runnable{

    @Override
    public void run() {
        this.process();
    }
}
