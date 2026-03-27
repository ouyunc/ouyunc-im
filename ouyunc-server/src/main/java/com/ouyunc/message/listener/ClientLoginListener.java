package com.ouyunc.message.listener;

import com.ouyunc.base.constant.enums.DeviceTypeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.NetworkEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.payload.ClientLoginEventPayload;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.domain.constants.YesOrNo;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.protocol.NativePacketProtocol;
import com.ouyunc.repository.DefaultRepository;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

/**
 * @Author fzx
 * @Description: 客户端登录成功事件监听器
 **/
public class ClientLoginListener implements MessageListener<MessageEvent> {

    private static final Logger log = LoggerFactory.getLogger(ClientLoginListener.class);


    /**
     * @Author fzx
     * @Description 处理客户端上线事件/成功登录的时候会触发
     */
    @Override
    public EventType type() {
        return MessageEventTypeEnum.CLIENT_LOGIN;
    }

    @Override
    public void onEvent(MessageEvent event) {
        if (log.isDebugEnabled()) {
            log.debug("客户端上线事件监听器正在处理：{}", event);
        }
        Object source = event.getSource();
        if (!(source instanceof ClientLoginEventPayload payload)) {
            return;
        }
        Object login = payload.loginInfo();
        if (login == null) {
            return;
        }
        if (login instanceof LoginClientInfo loginClientInfo && loginClientInfo.getEnableAlive() == YesOrNo.YES.getCode()) {
            // 这里其实也可以设置是否开启上线消息，来推送给客户端,找到当前好友的所有在线好友，然后推给所有在线好友
            String identity = loginClientInfo.getIdentity();
            String appKey = loginClientInfo.getAppKey();
            Collection<String> friendIds = DefaultRepository.INSTANCE.getFriendIds(appKey, identity);
            // 获取在线的好友向其发送退出登录的消息
            for (String friendId : friendIds) {
                List<LoginClientInfo> loginClientInfos = ClientHelper.onlineAll(appKey, friendId);
                if (CollectionUtils.isNotEmpty(loginClientInfos)) {
                    Metadata metadata = new Metadata();
                    metadata.setAppKey(appKey);
                    Message message = new Message(MessageContext.idGenerator().generateIdStr(),identity, friendId, MessageContentTypeEnum.TEXT_CONTENT.getType(), loginClientInfo.getAliveMessage(), TimeUtil.currentTimeMillis(), metadata);
                    Packet packet = new Packet(NativePacketProtocol.OUYUNC.getProtocol(), NativePacketProtocol.OUYUNC.getProtocolVersion(), MessageContext.idGenerator().generateId(), DeviceTypeEnum.PC.getType(), NetworkEnum.OTHER.getValue(), Encrypt.SymmetryEncrypt.NONE.getValue(), Serializer.PROTO_STUFF.getValue(), MessageTypeEnum.CLIENT_LOGIN.getType(), message);
                    MessageHelper.asyncSendMessage(packet, loginClientInfos);
                }
            }
        }
    }
}
