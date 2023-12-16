package com.ouyunc.im.event;

import java.time.Clock;

/**
 * @Author fangzhenxun
 * @Description: 用户离线事件
 **/
public class IMOfflineEvent extends IMEvent {
    public IMOfflineEvent(Object source) {
        super(source);
    }

    public IMOfflineEvent(Object source, Clock clock) {
        super(source, clock);
    }
}
