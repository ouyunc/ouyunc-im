package com.ouyunc.message.validator;

import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fzx
 * @description 好友校验
 */
public enum FriendValidator implements Validator<Packet> {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(FriendValidator.class);


    /***
     * @author fzx
     * @description 校验是否是好友，是好友返回true, 否则返回false
     */
    @Override
    public boolean verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        // 判断是否是好友，有可能mq 延迟消费
        if (!DefaultRepository.INSTANCE.isFriend(metadata.getAppKey(), to, from)) {
            log.info("{} 和 {} 不是好友关系: {}", from, to, packet);
            return false;
        }
        return true;
    }
}
