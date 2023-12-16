package com.ouyunc.im.listener;

import com.ouyunc.im.event.IMOfflineEvent;

/**
 * @Author fangzhenxun
 * @Description: im 离线监听器
 **/
public class IMOfflineListener implements IMListener<IMOfflineEvent> {


    /**
     * @Author fangzhenxun
     * @Description 处理客户端离线事件，比如发送离线预警到mq
     * @param event
     * @return
     */
    @Override
    public void onApplicationEvent(IMOfflineEvent event) {
        // do nothing
    }
}
