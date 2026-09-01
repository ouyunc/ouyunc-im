package com.ouyunc.repository;

import com.ouyunc.base.packet.Packet;

import java.util.concurrent.Future;

/**
 * @author fzx
 * @description 持久化仓库接口
 */
public interface Repository {

    /**
     * 异步全局保存协议包（旁路投递到归档 MQ）。
     * <p>不阻塞调用方，返回值可忽略；失败仍记日志并发布异常事件。
     * 需要等待发送结果时再对返回的 {@link Future} 取结果或挂回调。</p>
     *
     * @param packet 待归档协议包
     * @return MQ 发送 Future；启动发送即失败时为已完成的异常 Future
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
