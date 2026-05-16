package com.ouyunc.repository;

import com.ouyunc.base.constant.enums.DeviceType;
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
     * 检查 QoS 重发是否重复（packetId 优先，其次通道身份 + 客户端 messageId）
     *
     * @param packet 待检消息（含内嵌 packetId 或客户端 messageId）
     * @param channelLoginIdentity 当前连接登录身份，用于 cli 层幂等，可为 null
     */
    boolean checkDup(Packet packet, String channelLoginIdentity);

}
