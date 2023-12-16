package com.ouyunc.im.listener;

import com.ouyunc.im.event.IMOnlineEvent;

/**
 * @Author fangzhenxun
 * @Description: im 在线监听器
 **/
public class IMOnlineListener implements IMListener<IMOnlineEvent> {


    /**
     * @Author fangzhenxun
     * @Description 处理客户端在线事件，比如发送在线到mq
     * @param event
     * @return void
     */
    @Override
    public void onApplicationEvent(IMOnlineEvent event) {
        // do nothing
    }
}
