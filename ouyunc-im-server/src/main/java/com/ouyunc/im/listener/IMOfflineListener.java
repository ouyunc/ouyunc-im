package com.ouyunc.im.listener;

import com.ouyunc.im.event.IMOfflineEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: im 离线监听器
 **/
public class IMOfflineListener implements IMListener<IMOfflineEvent> {
    private static Logger log = LoggerFactory.getLogger(IMOfflineListener.class);


    /**
     * @Author fangzhenxun
     * @Description 处理客户端离线事件，比如发送离线预警到mq
     * @param event
     * @return
     */
    @Override
    public void onApplicationEvent(IMOfflineEvent event) {
        if (log.isDebugEnabled()) {
            log.debug("im 离线事件监听器正在处理：{}", event);
        }
        // do nothing
    }
}
