package com.ouyunc.message.router;

/**
 * @author fzx
 * @description 路由器
 */
public interface Router<R,S,T> {

    /**
     * @Description 查找并返回可用的路由信息
     */
    R route(S source, T target);
}
