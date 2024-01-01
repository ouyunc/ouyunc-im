package com.ouyunc.im.listener;

import com.ouyunc.im.event.IMOnlineEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: im 在线监听器
 **/
public class IMOnlineListener implements IMListener<IMOnlineEvent> {
    private static Logger log = LoggerFactory.getLogger(IMOnlineListener.class);


    /**
     * @Author fangzhenxun
     * @Description 处理客户端在线事件，比如发送在线到mq
     * @param event
     * @return void
     */
    @Override
    public void onApplicationEvent(IMOnlineEvent event) {
        if (log.isDebugEnabled()) {
            log.debug("im 上线事件监听器正在处理：{}", event);
        }
        // do nothing
    }
}
