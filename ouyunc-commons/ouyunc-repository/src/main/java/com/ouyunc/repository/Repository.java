package com.ouyunc.repository;

import com.ouyunc.base.packet.Packet;

/**
 * @author fzx
 * @description 持久化仓库接口
 */
public interface Repository {

    /***
     * @author fzx
     * @description 全局保存协议包，留档使用
     */
    void save(Packet packet);

}
