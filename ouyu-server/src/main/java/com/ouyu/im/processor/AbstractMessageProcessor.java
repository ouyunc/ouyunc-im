package com.ouyu.im.processor;

import com.ouyu.im.constant.enums.MessageEnum;

/**
 * @Author fangzhenxun
 * @Description: 消息处理类
 * @Version V1.0
 **/
public abstract class AbstractMessageProcessor implements MessageProcessor {

    /**
     * 标识子类处理消息的类型，如果一个子类处理多个类型使用 | 逻辑或进行返回
     */
    public abstract MessageEnum messageType();
}
