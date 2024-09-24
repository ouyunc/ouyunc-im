package com.ouyunc.client;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.SnowflakeUtil;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author fzx
 * @description 客户端启动
 */
public class StartClient {    private static final ScheduledExecutorService SCHEDULED_EVENT_EXECUTORS = Executors.newScheduledThreadPool(16, new BasicThreadFactory.Builder().namingPattern("client-heart-hart-pool-%d").build());

    public static void main(String[] args) {
        MessageClient messageClient = new DefaultMessageClient();
        messageClient.configure(null);
        LoginContent loginContent = new LoginContent();
        loginContent.setAppKey("ouyunc");
        loginContent.setIdentity("18856895462");
        loginContent.setDeviceType(DeviceTypeEnum.PC);
        Message message = new Message("123456:1", "18856895462", WsMessageContentTypeEnum.LOGIN_REQUEST_CONTENT.getType(), JSON.toJSONString(loginContent), Clock.systemUTC().millis());
        Packet packet = new Packet(ProtocolTypeEnum.WS.getProtocol(), ProtocolTypeEnum.WS.getProtocolVersion(), SnowflakeUtil.nextId(), DeviceTypeEnum.PC.getDeviceTypeValue(), NetworkEnum.OTHER.getValue(), Encrypt.SymmetryEncrypt.NONE.getValue(), Serializer.PROTO_STUFF.getValue(), WsMessageTypeEnum.LOGIN.getType(), message);

        MessageClientTemplate.syncSendMessage(packet);

        SCHEDULED_EVENT_EXECUTORS.schedule(new Runnable() {
            @Override
            public void run() {
                LoginContent loginContent = new LoginContent();
                loginContent.setAppKey("ouyunc");
                loginContent.setIdentity("18856895462");
                loginContent.setDeviceType(DeviceTypeEnum.PC);
                Message message = new Message("123456:1", "18856895462", WsMessageContentTypeEnum.LOGIN_REQUEST_CONTENT.getType(), JSON.toJSONString(loginContent), Clock.systemUTC().millis());
                Packet packet = new Packet(ProtocolTypeEnum.WS.getProtocol(), ProtocolTypeEnum.WS.getProtocolVersion(), SnowflakeUtil.nextId(), DeviceTypeEnum.PC.getDeviceTypeValue(), NetworkEnum.OTHER.getValue(), Encrypt.SymmetryEncrypt.NONE.getValue(), Serializer.PROTO_STUFF.getValue(), WsMessageTypeEnum.LOGIN.getType(), message);

                //MessageClientTemplate.syncSendMessage(packet);
            }
        }, 10, TimeUnit.SECONDS);


    }
}
