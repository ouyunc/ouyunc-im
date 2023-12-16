package com.ouyunc.im.event;

import java.time.Clock;

/**
 * @Author fangzhenxun
 * @Description: 用户上线事件
 **/
public class IMOnlineEvent extends IMEvent {
    public IMOnlineEvent(Object source) {
        super(source);
    }

    public IMOnlineEvent(Object source, Clock clock) {
        super(source, clock);
    }
}
