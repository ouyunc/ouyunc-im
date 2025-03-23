package com.ouyunc.message.validator;

import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.domain.constants.YesOrNo;
import com.ouyunc.domain.entity.FriendEntity;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fzx
 * @description 屏蔽校验器
 */
public enum FriendShieldValidator implements Validator<Packet> {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(FriendShieldValidator.class);


    /***
     * @author fzx
     * @description 校验是否在屏蔽, 被屏蔽返回 true, 未被屏蔽返回false
     */
    @Override
    public boolean verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        FriendEntity friendEntity = DefaultRepository.INSTANCE.getFriend(metadata.getAppKey(), to, from);
        if (friendEntity != null) {
            log.info("{} 已经被 {} 屏蔽了", from, to);
            return YesOrNo.YES.getCode().equals(friendEntity.getShield());
        }
        // 基本不会走到这里，判断是否是好友，有可能mq 延迟消费
        return  !DefaultRepository.INSTANCE.isFriend(metadata.getAppKey(), to, from);
    }
}
