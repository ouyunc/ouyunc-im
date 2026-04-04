package com.ouyunc.message.processor.http;

import com.ouyunc.domain.http.MessagePushRequest;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.http.annotation.HttpRequestMapping;
import com.ouyunc.message.http.annotation.HttpRestController;
import com.ouyunc.message.http.annotation.PostHttpRequest;
import com.ouyunc.message.http.annotation.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IM HTTP 推送（类似 Spring {@code @RestController} + 方法映射）。
 */
@HttpRestController
@HttpRequestMapping("/api/im")
public class MessagePushController {

    private static final Logger log = LoggerFactory.getLogger(MessagePushController.class);

    @PostHttpRequest("/push")
    public Object push(@RequestBody MessagePushRequest body, HttpContext httpContext) {
        // @todo 待实现
        return null;
    }

}
