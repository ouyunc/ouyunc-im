package com.ouyunc.message.processor.http;

import com.ouyunc.domain.http.MessagePushRequest;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.http.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IM HTTP 推送（类似 Spring {@code @RestController} + 方法映射）。
 */
@HttpRestController
@HttpRequestMapping("/api/im")
public class MessagePushController {

    private static final Logger log = LoggerFactory.getLogger(MessagePushController.class);

    @PostHttpRequest("/message/push")
    public Object push(@RequestBody MessagePushRequest body, @RequestParam("s") String s,@RequestParam("t") String t, HttpContext httpContext) throws HttpPipelineException {
        // @todo 待实现
        return null;
    }

}
