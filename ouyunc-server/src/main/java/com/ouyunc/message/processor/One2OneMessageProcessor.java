package com.ouyunc.message.processor;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.core.listener.event.ReadReceiptMessageEvent;
import com.ouyunc.core.listener.event.SaveMessageEvent;
import com.ouyunc.core.listener.event.WithdrawMessageEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.validator.AuthValidator;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * 一对一（单聊）消息处理器
 */
public class One2OneMessageProcessor extends AbstractMessageProcessor<Byte>{
    private static final Logger log = LoggerFactory.getLogger(One2OneMessageProcessor.class);

    /**
     * redisson 客户端
     */
    private final RedissonClient redissonClient = CacheFactory.REDISSON.instance();

    @Override
    public MessageType type() {
        return MessageTypeEnum.ONE_2_ONE;
    }


    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 异步存储packet（目前只是保存相关信息，不做扩展，以后可以做数据分析使用），这里将该数据存储到时序数据库中
        messageProcessorExecutor.execute(() -> {
            repository().save(packet);
        });
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            // 关闭当前 channel，这里会触发 DefaultSocketChannelInitializer 中的关闭逻辑
            log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
            ctx.close();
            return;
        }
        // 校验是否拥有相关权限 permission （对方是否被拉黑，禁用等）

    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.info("One2OneMessageProcessor 正在处理一对一消息...");
        AbstractBaseProcessor<? extends Number> contentProcessor = MessageServerContext.messageContentProcessorCache.get(packet.getMessage().getContentType());
        if (contentProcessor != null) {
            contentProcessor.process(ctx, packet);
            return;
        }
        // 发送消息到对方
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        // 保存消息， 30 天 过期， 后面通过配置文件进行可配置
        String sessionId = IdentityUtil.sessionId(message.getFrom(), message.getTo());
        if (!repository().saveMessage(packet, sessionId, NumberConstant.NUMBER_30 * MessageConstant.DAY_TIMESTAMP)) {
            log.error("一对一消息: {} 保存消息异常", packet);
            MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存一对一消息异常!", packet), true);
            return;
        }
        // 保存到磁盘
        MessageServerContext.publishEvent(new SaveMessageEvent(packet), true);


        // 可以做额外的业务处理, 比如这里将消息撤回，已读，撤销已读做特殊处理;
        if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == message.getContentType()) {
            // 处理撤销消息，这里撤销是删除缓存中的消息，以及离线消息和会话消息
            // 加锁，防止获取消息的时候出现脏数据,这里使用联锁，因为在对方获取离线数据和会话数据时都可能出现脏数据
            RLock multiLock = redissonClient.getMultiLock(redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.SESSION + sessionId),
                    redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.OFFLINE + message.getTo()));
            try {
                if (multiLock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                    if (!repository().withdrawMessage(packet, sessionId)) {
                        // 未撤销成功
                        log.error("加锁成功，撤销消息异常，packet: {}", packet);
                        MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.WITHDRAW_MESSAGE_ERROR, "撤销消息异常!", packet), true);
                        return;
                    }
                }else {
                    log.error("尝试获取锁失败，撤销消息异常，packet: {}", packet);
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.WITHDRAW_MESSAGE_ERROR, "撤销消息异常!", packet), true);
                    return;
                }
            } catch (Exception e) {
                log.error("尝试获取锁失败，packet: {}", packet);
                MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.WITHDRAW_MESSAGE_ERROR, "撤销消息异常!", packet), true);
                return;
            } finally {
                if (multiLock.isHeldByCurrentThread()) {
                    multiLock.unlock();
                }
            }
            // 撤销磁盘的消息，注意：这里可能会出现一种情况，先删除redis 中的数据，后通过异步事件操作删除的持久化的数据；
            // 假如A正要执行删除缓存中的数据，
            // B突然上线或者拉取会话中的消息id,
            // 此时A删除缓存成功，B从缓存查询数据没有查到，然后从持久化中查询，此时B会查到数据（A还没来得及修改的老数据）
            // 然后将数据添加到缓存中，会导致脏数据；如何解决呢？
            // 这里提供一种简单的解决方式：可以在持久化修改后再次删除热点消息；存在短暂的脏数据。如果持久化异常，会进行监控池中最后通过人工或者其他流程操作最终一致性；
            // 也可以通过加锁的方式，在撤销这里加锁，以及获取消息的时候加锁，这样保证数据一致性，但是会增加锁的开销。
            MessageServerContext.publishEvent(new WithdrawMessageEvent(packet), true);
        }else if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == message.getContentType()) {
            // 处理读已回执消息内容
            // 加锁，防止获取消息的时候出现脏数据,这里使用联锁，因为在对方获取离线数据和会话数据时都可能出现脏数据
            RLock multiLock = redissonClient.getMultiLock(redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.SESSION + sessionId),
                    redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.OFFLINE + message.getTo()));
            try {
                if (multiLock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                    if (!repository().readReceiptMessage(packet, NumberConstant.NUMBER_30 * MessageConstant.DAY_TIMESTAMP)) {
                        // 未撤销成功
                        log.error("读已回执消息异常，packet: {}", packet);
                        MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR, "读已回执消息异常!", packet), true);
                        return;
                    }
                }else {
                    log.error("尝试获取锁失败，已读回执消息异常，packet: {}", packet);
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR, "读已回执消息异常!", packet), true);
                    return;
                }
            } catch (Exception e) {
                log.error("尝试获取锁失败，packet: {}", packet);
                MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR, "读已回执消息异常!", packet), true);
                return;
            } finally {
                if (multiLock.isHeldByCurrentThread()) {
                    multiLock.unlock();
                }
            }
            MessageServerContext.publishEvent(new ReadReceiptMessageEvent(packet), true);
        }

        // 获取对方在线的所有客户端
        List<LoginClientInfo> toLoginClientInfos = ClientHelper.onlineAll(message.getMetadata().getAppKey(), message.getTo());
        // 如果不在线的话，先保存到离线消息队列中，然后发送消息到对方
        if (CollectionUtils.isEmpty(toLoginClientInfos)) {
            log.warn("发送消息到: {} 失败, 对方不在线!,消息已存储到离线队列中", message.getTo());
            return;
        }
        // 异步发送消息
        MessageHelper.asyncSendMessage(packet, toLoginClientInfos);
    }
}
