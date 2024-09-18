package com.ouyunc.client.selector;

import java.net.URISyntaxException;

/**
 * @Author fzx
 * @Description: 协议分选择器
 **/
public interface ProtocolSelector<T, U> {

    /***
     * @author fzx
     * @description 匹配不同的协议,
     */
    boolean match(T t);

    /**
     * @Author fzx
     * @Description 处理逻辑
     */
    void process(U u) throws URISyntaxException;
}
