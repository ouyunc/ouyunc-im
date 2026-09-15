package com.ouyunc.client;

import com.ouyunc.base.constant.enums.SendStatusEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.MessageProtocol;
import com.ouyunc.base.model.SendCallback;
import com.ouyunc.base.model.SendResult;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.client.base.ChannelPoolKey;
import com.ouyunc.client.pool.MessageClientPool;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.util.concurrent.Future;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 客户端发送模板。
 * <p>写结果检查当前 {@code writeAndFlush} Future；目标地址来自初始化配置或显式入参。
 * {@link #syncSendMessage} 明确等待写完成（带超时），禁止在 EventLoop 上调用。</p>
 */
public class MessageClientTemplate {

    private static final Logger log = LoggerFactory.getLogger(MessageClientTemplate.class);

    /** 同步发送默认等待上限，避免无限阻塞。 */
    private static final long SYNC_SEND_TIMEOUT_SECONDS = 10L;

    private MessageClientTemplate() {
    }

    /**
     * 同步发送：阻塞等待写完成（含失败）。勿在 Netty EventLoop 线程调用。
     */
    public static SendResult syncSendMessage(Packet packet) {
        return syncSendMessage(packet, MessageClientPool.defaultServerAddress());
    }

    public static SendResult syncSendMessage(Packet packet, String serverAddress) {
        try {
            return sendAsync(packet, serverAddress)
                    .toCompletableFuture()
                    .get(SYNC_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return SendResult.builder()
                    .sendStatus(SendStatusEnum.SEND_FAIL)
                    .packet(packet)
                    .exception(new MessageException("同步发送超时: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return SendResult.builder()
                    .sendStatus(SendStatusEnum.SEND_FAIL)
                    .packet(packet)
                    .exception(cause)
                    .build();
        }
    }

    public static CompletionStage<SendResult> sendAsync(Packet packet) {
        return sendAsync(packet, MessageClientPool.defaultServerAddress());
    }

    public static CompletionStage<SendResult> sendAsync(Packet packet, String serverAddress) {
        CompletableFuture<SendResult> result = new CompletableFuture<>();
        doSendMessage(packet, serverAddress, result::complete);
        return result;
    }

    public static void asyncSendMessage(Packet packet, SendCallback sendCallback) {
        asyncSendMessage(packet, MessageClientPool.defaultServerAddress(), sendCallback);
    }

    public static void asyncSendMessage(Packet packet, String serverAddress, SendCallback sendCallback) {
        SendCallback callback = sendCallback == null ? ignored -> { } : sendCallback;
        doSendMessage(packet, serverAddress, callback);
    }

    private static void doSendMessage(Packet packet, String serverAddress, SendCallback sendCallback) {
        if (packet == null) {
            sendCallback.onCallback(SendResult.builder()
                    .sendStatus(SendStatusEnum.SEND_FAIL)
                    .exception(new MessageException("packet 不能为空"))
                    .build());
            return;
        }
        if (StringUtils.isBlank(serverAddress)) {
            log.error("发送目标地址未配置，请先 MessageClient.configure / MessageClientPool.init");
            sendCallback.onCallback(SendResult.builder()
                    .sendStatus(SendStatusEnum.SEND_FAIL)
                    .packet(packet)
                    .exception(new MessageException("消息发送失败：未配置远端地址"))
                    .build());
            return;
        }

        ChannelPoolKey poolKey = new ChannelPoolKey(
                new MessageProtocol(packet.getProtocol(), packet.getProtocolVersion()), serverAddress);
        SimpleChannelPool channelPool = MessageClientPool.clientSimpleChannelPoolMap.get(poolKey);
        if (channelPool == null) {
            log.error("获取不到 channelPool, 请检查是否已经调用了 init 方法, serverAddress={}", serverAddress);
            sendCallback.onCallback(SendResult.builder()
                    .sendStatus(SendStatusEnum.SEND_FAIL)
                    .packet(packet)
                    .exception(new MessageException("消息发送失败：无可用连接池"))
                    .build());
            return;
        }

        Future<Channel> acquireFuture = channelPool.acquire();
        acquireFuture.addListener(af -> {
            if (!af.isDone()) {
                return;
            }
            if (!af.isSuccess()) {
                log.error("获取 channel 失败 serverAddress={}", serverAddress, af.cause());
                sendCallback.onCallback(SendResult.builder()
                        .sendStatus(SendStatusEnum.SEND_FAIL)
                        .packet(packet)
                        .exception(af.cause())
                        .build());
                return;
            }
            // Listener 泛型为 ?，需从具体 acquireFuture 取 Channel
            Channel channel = acquireFuture.getNow();
            if (channel == null) {
                sendCallback.onCallback(SendResult.builder()
                        .sendStatus(SendStatusEnum.SEND_FAIL)
                        .packet(packet)
                        .exception(new MessageException("消息发送失败：acquire 返回空 channel"))
                        .build());
                return;
            }
            ChannelFuture writeFuture = channel.writeAndFlush(packet);
            writeFuture.addListener(wf -> {
                try {
                    // 必须检查写 Future，不能误用外层 acquire Future
                    if (wf.isSuccess()) {
                        sendCallback.onCallback(SendResult.builder()
                                .sendStatus(SendStatusEnum.SEND_OK)
                                .packet(packet)
                                .build());
                    } else {
                        sendCallback.onCallback(SendResult.builder()
                                .sendStatus(SendStatusEnum.SEND_FAIL)
                                .packet(packet)
                                .exception(wf.cause())
                                .build());
                    }
                } finally {
                    channelPool.release(channel);
                }
            });
        });
    }
}
