package com.ouyunc.message.listener;

import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.IpUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.MessageEventListener;
import com.ouyunc.core.listener.EventListener;
import com.ouyunc.core.listener.event.payload.ClientLoginEventPayload;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.base.constant.enums.YesOrNo;
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
 */
@EventListener( ring = EventRingEnum.CLIENT_LOGIN)
class ClientLoginMessageEventListener implements MessageEventListener<MessageEvent> {

    private static final Logger log = LoggerFactory.getLogger(ClientLoginMessageEventListener.class);


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
        long consumeLagMs = Math.max(0L, TimeUtil.currentTimeMillis() - event.getPublishTime());
        Object source = event.getSource();
        if (!(source instanceof ClientLoginEventPayload payload)) {
            log.warn("[客户端登录] 忽略无效事件, eventId={}, publishTime={}, consumeLagMs={}, sourceType={}",
                    event.getId(), event.getPublishTime(), consumeLagMs,
                    source == null ? "null" : source.getClass().getName());
            return;
        }
        Object login = payload.loginInfo();
        if (login == null) {
            log.warn("[客户端登录] loginInfo 为空, eventId={}, publishTime={}, consumeLagMs={}",
                    event.getId(), event.getPublishTime(), consumeLagMs);
            return;
        }
        if (!(login instanceof LoginClientInfo loginClientInfo)) {
            log.warn("[客户端登录] loginInfo 类型不支持, eventId={}, publishTime={}, consumeLagMs={}, loginInfoType={}",
                    event.getId(), event.getPublishTime(), consumeLagMs, login.getClass().getName());
            return;
        }
        String remoteIp = payload.ctx() != null ? IpUtil.getIp(payload.ctx()) : "unknown";
        if (loginClientInfo.getEnableAlive() != YesOrNo.YES.getCode()) {
            log.info("[客户端登录] 已接收(未开启上线通知), eventId={}, appKey={}, identity={}, deviceType={}, remoteIp={}, loginServer={}, consumeLagMs={}",
                    event.getId(), loginClientInfo.getAppKey(), loginClientInfo.getIdentity(),
                    loginClientInfo.getDeviceType(), remoteIp, loginClientInfo.getLoginServerAddress(), consumeLagMs);
            return;
        }
        String identity = loginClientInfo.getIdentity();
        String appKey = loginClientInfo.getAppKey();
        Collection<String> friendIds = DefaultRepository.INSTANCE.getFriendIds(appKey, identity);
        int notifiedFriends = 0;
        int notifiedSessions = 0;
        for (String friendId : friendIds) {
            List<LoginClientInfo> loginClientInfos = ClientHelper.onlineAll(appKey, friendId);
            if (CollectionUtils.isNotEmpty(loginClientInfos)) {
                notifiedFriends++;
                notifiedSessions += loginClientInfos.size();
                Metadata metadata = new Metadata();
                metadata.setAppKey(appKey);
                Message message = new Message(MessageContext.idGenerator().generateIdStr(),identity, friendId, MessageContentTypeEnum.TEXT_CONTENT.getType(), loginClientInfo.getAliveMessage(), TimeUtil.currentTimeMillis(), metadata);
                Packet packet = new Packet(NativePacketProtocol.OUYUNC.getProtocol(), NativePacketProtocol.OUYUNC.getProtocolVersion(), MessageContext.idGenerator().generateId(), DeviceTypeEnum.PC.getType(), NetworkEnum.OTHER.getValue(), Encrypt.SymmetryEncrypt.NONE.getValue(), Serializer.PROTO_STUFF.getValue(), MessageTypeEnum.CLIENT_LOGIN.getType(), message);
                MessageHelper.asyncSendMessage(packet, loginClientInfos);
            }
        }
        log.info("[客户端登录] 已处理, eventId={}, appKey={}, identity={}, deviceType={}, remoteIp={}, loginServer={}, friendCount={}, notifiedFriends={}, notifiedSessions={}, consumeLagMs={}",
                event.getId(), appKey, identity, loginClientInfo.getDeviceType(), remoteIp,
                loginClientInfo.getLoginServerAddress(), friendIds.size(), notifiedFriends, notifiedSessions, consumeLagMs);
    }
}
