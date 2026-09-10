package com.ouyunc.message.processor.http;

import com.ouyunc.base.constant.HttpRequestConstant;
import com.ouyunc.base.model.HttpResponseResult;
import com.ouyunc.base.model.RelationCacheInvalidateEvent;
import com.ouyunc.message.cluster.RelationCacheInvalidateSupport;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.http.annotation.HttpRequestMapping;
import com.ouyunc.message.http.annotation.HttpRestController;
import com.ouyunc.message.http.annotation.PostHttpRequest;
import com.ouyunc.message.http.annotation.RequestBody;
import org.apache.commons.lang3.StringUtils;

/**
 * 业务侧 Redis 写完后 HTTP 通知本节点清关系 Caffeine，再集群同步。
 */
@HttpRestController
@HttpRequestMapping
public class RelationCacheInvalidateController {

    @PostHttpRequest(HttpRequestConstant.HTTP_ADMIN_RELATION_CACHE_INVALIDATE_PATH)
    public HttpResponseResult<Void> invalidate(@RequestBody RelationCacheInvalidateEvent event,
                                               HttpContext httpContext) {
        if (event == null) {
            return HttpResponseResult.fail("请求体不能为空");
        }
        if (StringUtils.isBlank(event.getAppKey())) {
            event.setAppKey(httpContext.getAppKey());
        }
        RelationCacheInvalidateSupport.applyLocalAndFanout(event);
        return HttpResponseResult.success();
    }
}
