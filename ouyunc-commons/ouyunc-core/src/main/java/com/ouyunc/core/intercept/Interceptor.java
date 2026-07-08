package com.ouyunc.core.intercept;

import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;

/**
 * 拦截器接口, 可以使用注解com.ouyunc.base.model.Order-> @Order 指定拦截器的执行顺序,值越小优先级越高
 */
public interface Interceptor {

    /**
     * 前置处理,返回true 放行，false 拦截
     *
     * @return
     */
    boolean preHandle(Packet packet, Target target);

    /**
     * 后置处理
     */
    void postHandle(Packet packet, Target target);
}
