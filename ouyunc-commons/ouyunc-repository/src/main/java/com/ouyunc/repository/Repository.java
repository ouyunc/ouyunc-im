package com.ouyunc.repository;

import com.ouyunc.base.packet.Packet;

import java.util.concurrent.Future;

/**
 * @author fzx
 * @description 持久化仓库接口
 */
public interface Repository {

    /***
     * @author fzx
     * @description 异步全局保存协议包，保存成功返回true,失败返回false
     */
    Future<?> save(Packet packet);

    /**
     * 检查消息是否重复发送（也就是是否已经持久化）
     * @param packet
     * @return
     */
    boolean checkDup(Packet packet);

}
