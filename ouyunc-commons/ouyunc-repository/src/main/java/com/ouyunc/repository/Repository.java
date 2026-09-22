package com.ouyunc.repository;

import com.ouyunc.base.packet.Packet;

import java.util.concurrent.Future;

/**
 * @author fzx
 * @description 持久化仓库接口
 */
public interface Repository {

    /**
     * 确认归档协议包到 MQ：等 broker ACK 后才算成功。
     * <p>Kafka key / 冷库幂等为 {@code appKey:messageId}。调用线程会先快照 {@code packet}，
     * JSON 与投递在仓库线程池执行，不阻塞调用方。失败不写 Outbox，由客户端 QoS 重试。勿在 IO 线程 {@code get()}。</p>
     *
     * @param packet 待归档协议包
     * @return MQ 发送 Future；启动发送即失败时为已完成的异常 Future
     */
    Future<?> save(Packet packet);

    /**
     * 检查 QoS 重发是否重复。权威键为登录身份 + 客户端 {@code messageId}；
     * 仅 COMMITTED 且拿到正式 packetId 算重复，成功时将 packet 收敛到该正式 ID。
     * PENDING 占位不能当成功。
     *
     * @param packet 待检消息
     * @param channelLoginIdentity 当前连接登录身份，用于 cli 层幂等，可为 null
     */
    boolean checkDup(Packet packet, String channelLoginIdentity);

}
