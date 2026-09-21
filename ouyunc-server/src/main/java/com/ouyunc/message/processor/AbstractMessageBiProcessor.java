package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.message.helper.MessageArchiveHelper;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.validator.AuthValidator;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 消息抽象处理类：统一三阶段 API。
 * <ul>
 *   <li>{@link #preProcess} — 鉴权/校验/QoS 展开/归档；返回 true 才进入 process</li>
 *   <li>{@link #process} — 落库、投递、回执等业务</li>
 *   <li>{@link #postProcess} — 轻量收尾，默认空</li>
 * </ul>
 */
public abstract class AbstractMessageBiProcessor<T extends Number> extends AbstractBaseBiProcessor<Mono<Void>, T> {

    private static final Logger log = LoggerFactory.getLogger(AbstractMessageBiProcessor.class);

    /**
     * 获取数据存储实现类, 子类可以重写来实现自定义存储实现
     */
    @SuppressWarnings("unchecked")
    public DefaultRepository repository() {
        return DefaultRepository.INSTANCE;
    }

    /**
     * 前置阶段：鉴权、业务校验、QoS 展开、原文归档确认。
     *
     * @return {@code true} 进入 {@link #process}；{@code false} 结束本条消息链（已处理完毕或已拒绝）
     */
    public Mono<Boolean> preProcess(ChannelHandlerContext ctx, Packet packet) {
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
            MessageContext.publishEvent(new MessageEvent(
                    ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过", packet),
                    MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return Mono.just(false);
        }
        if (MessageContext.isQosEnable() && qosPreHandle(ctx, packet)) {
            // 幂等命中已 ACK，不再进 process
            return Mono.just(false);
        }
        return archiveAfterAuth(packet).thenReturn(true);
    }

    /**
     * 登录鉴权 +（可选）业务校验均通过后归档并等待 MQ 确认。未登录包不得进入 MQ；
     * 权限拒绝的包也不归档（由 {@link #continueWhenPassed} 在通过后再调用）。
     */
    protected Mono<Void> archiveAfterAuth(Packet packet) {
        // 原文先归档用于审计；只有 MQ 确认后才允许业务提交/成功 ACK。
        // 不因连接取消撤销已开始的归档，避免取消 future 影响失败补偿。
        return MessageArchiveHelper.confirm(() -> repository().save(packet));
    }

    /**
     * 业务校验通过后归档并返回 true；拒绝则回调 onReject 并返回 false。
     *
     * @param shouldReject true 表示拦截
     * @param onReject     拦截时回调（如释放 QoS claim 或回 ACK），可为 null
     * @param rejectLog    拒绝日志模板，可含一个 {@code {}} 占位 packet
     */
    protected Mono<Boolean> continueWhenPassed(Packet packet, Mono<Boolean> shouldReject,
                                               Runnable onReject, String rejectLog) {
        return shouldReject
                .onErrorResume(error -> {
                    log.error("校验过程中出现异常: {}", error.getMessage());
                    return Mono.just(true);
                })
                .flatMap(reject -> {
                    if (Boolean.TRUE.equals(reject)) {
                        log.warn(rejectLog, packet);
                        if (onReject != null) {
                            onReject.run();
                        }
                        return Mono.just(false);
                    }
                    return archiveAfterAuth(packet).thenReturn(true);
                });
    }

    /**
     * 好友/群请求：权限拒绝视为已定性，回 S2C ACK 停 QoS 重试（聊天消息仍应走 {@link #continueWhenPassed} 释放 claim）。
     */
    protected Mono<Boolean> continueWhenPassedOrAck(ChannelHandlerContext ctx, Packet packet,
                                                    Mono<Boolean> shouldReject, String rejectLog) {
        return continueWhenPassed(packet, shouldReject, () -> ackRequestSettled(ctx, packet), rejectLog);
    }

    /**
     * 请求已定性（幂等忽略 / 客户端错误 / 无会话）或 Redis 成功：回 ACK。写库或绑定失败不要调用，以便客户端重试。
     */
    protected void ackRequestSettled(ChannelHandlerContext ctx, Packet packet) {
        qosPostHandle(ctx, packet);
    }

    /**
     * 业务事件先等 MQ broker 确认，再跑 Redis/通知。确认超时 20s，禁止放进 5s 锁内。
     * 失败不 ACK，交给客户端重试；不写 Outbox。
     */
    protected Mono<Void> confirmThenRun(String topic, String key, Packet packet, Runnable next) {
        return MessageArchiveHelper.confirm(() -> repository().publishPacketConfirmed(topic, key, packet))
                .then(Mono.fromRunnable(next));
    }

    /**
     * 后置阶段：默认无操作。指标/清理可覆写；不要在此发业务成功 ACK。
     */
    public Mono<Void> postProcess(ChannelHandlerContext ctx, Packet packet) {
        return Mono.empty();
    }
}
