package com.ouyunc.message.processor;

import com.ouyunc.core.exception.ExceptionReporter;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.helper.MessageAcceptPipelineHelper;
import com.ouyunc.message.validator.AuthValidator;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 消息抽象处理类：仅保留三阶段 API。
 *
 * <h3>标准受理模型（单聊/群聊主路径）</h3>
 * <pre>
 * 鉴权/业务校验通过（preProcess，不归档）
 *   → 内容/引用规范化
 *   → 内容安全（REJECT 不归档；MASK 改写正文）
 *   → 等 MQ SAVE 成功/失败（失败：不写 Redis、不投递，返回 UNKNOWN）
 *   → Redis 热写
 *   → SUCCESS/DUPLICATE 返回 ACCEPTED，失败返回对应受理结果
 *   → 仅 SUCCESS 时扇出（尽力而为）
 * </pre>
 *
 * <p>{@link #preProcess} / {@link #process} 允许子类整体覆写。
 * 管线细节见 {@link MessageAcceptPipelineHelper}，勿在本类堆叠辅助方法。</p>
 *
 * <ul>
 *   <li>{@link #preProcess} — 鉴权/校验/QoS 判重；单聊/群聊不在此归档；返回 true 才进入 process</li>
 *   <li>{@link #process} — 规范化、内容安全、MQ 归档、Redis 热写、受理结果、投递等</li>
 *   <li>{@link #postProcess} — 轻量收尾，默认空；不要在此重复发送受理结果</li>
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
     * 默认门闸：鉴权 → QoS 判重（COMMITTED 则回 ACCEPTED 并结束）→ MQ 归档确认。
     * <p>好友/群请求等可覆写后调用 {@link MessageAcceptPipelineHelper#continueWhenPassedOrAck}。
     * 单聊/群聊请用 {@link MessageAcceptPipelineHelper#gateWhenPassed}，并在 process 内
     * {@link MessageAcceptPipelineHelper#archiveAfterContentReady}。</p>
     *
     * @return {@code true} 进入 {@link #process}；{@code false} 结束本条消息链
     */
    public Mono<Boolean> preProcess(ChannelHandlerContext ctx, Packet packet) {
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过", "AbstractMessageBiProcessor.preProcess", packet);
            ctx.close();
            return Mono.just(false);
        }
        if (qosPreHandle(ctx, packet)) {
            // 幂等命中已 ACK，不再进 process、不再打 MQ
            return Mono.just(false);
        }
        return MessageAcceptPipelineHelper.archiveAfterAuth(packet).thenReturn(true);
    }

    /**
     * 后置阶段：默认无操作。指标/清理可覆写；不要在此发业务成功 ACK。
     */
    public Mono<Void> postProcess(ChannelHandlerContext ctx, Packet packet) {
        return Mono.empty();
    }
}
