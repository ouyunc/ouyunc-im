package com.ouyunc.im.processor.content;

import com.ouyunc.im.constant.enums.MessageContentEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 消息内容抽象处理类
 * @Version V3.0
 **/
public abstract class AbstractMessageContentProcessor implements MessageContentProcessor {
    private static Logger log = LoggerFactory.getLogger(AbstractMessageContentProcessor.class);

    /**
     * 消息内容类型
     */
    public abstract MessageContentEnum messageContentType();
}
