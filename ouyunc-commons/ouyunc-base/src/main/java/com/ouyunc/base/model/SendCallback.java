package com.ouyunc.base.model;

/**
 * @Author fzx
 * @Description: 发送回调接口
 **/
@FunctionalInterface
public interface SendCallback {
    /**
     * @Author fzx
     * @Description 发送消息回调接口
     * @param sendResult
     * @return void
     */
    void onCallback(SendResult sendResult);
}
