package com.ouyunc.message.processor.http;

import com.ouyunc.base.constant.HttpRequestConstant;
import com.ouyunc.base.model.HttpResponseResult;
import com.ouyunc.base.model.MessagePushRequest;
import com.ouyunc.base.model.MessagePushResponse;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.http.annotation.*;
import com.ouyunc.message.processor.http.push.InternalPacketIngressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

/**
 * IM HTTP 推送：统一外部推送入口。校验与幂等通过后异步投递并返回 ACCEPTED（不等于已投递）。
 */
@HttpRestController
@HttpRequestMapping("/api/im")
public class MessagePushController {

    private static final Logger log = LoggerFactory.getLogger(MessagePushController.class);

    @PostHttpRequest("/message/push")
    public CompletionStage<HttpResponseResult<MessagePushResponse>> push(
            @RequestBody MessagePushRequest body,
            @RequestHeader(value = HttpRequestConstant.HTTP_HEADER_REQUEST_ID) String requestId,
            HttpContext httpContext) throws HttpPipelineException {
        if (log.isDebugEnabled()) {
            log.debug("HTTP push request, appKey={}, messageId={}", httpContext.getAppKey(),
                    body != null ? body.getMessageId() : null);
        }
        return InternalPacketIngressService.push(body, httpContext);
    }
}
