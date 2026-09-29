package com.ouyunc.base.packet;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.ImageContent;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.packet.message.content.MessageContents;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.ImRouteCodec;
import com.ouyunc.base.utils.LoginSignatureUtil;
import com.ouyunc.base.utils.MD5Util;
import com.ouyunc.base.utils.PacketReaderWriterUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 功能矩阵：每种消息类型、每种内容类型都走 JSON 编解码往返；
 * 登录签名、多设备身份、集群路由字段在同一条用例里联动。
 */
class FeatureMatrixTest {

    @ParameterizedTest
    @EnumSource(MessageTypeEnum.class)
    void everyMessageTypeRoundTrips(MessageTypeEnum type) {
        Message message = new Message();
        message.setId("m-" + type.getName());
        message.setFrom("app:alice:1");
        message.setTo("app:bob:1");
        message.setContentType(MessageContentTypeEnum.PING_PONG_CONTENT.getType());
        message.setContent("ping");
        Packet decoded = writeThenRead(packet(type.getType(), message));
        assertEquals(type.getType(), decoded.getMessageType());
        assertEquals("ping", decoded.getMessage().getContent());
        assertEquals("app:alice:1", decoded.getMessage().getFrom());
    }

    @ParameterizedTest
    @EnumSource(MessageContentTypeEnum.class)
    void everyContentTypeRoundTrips(MessageContentTypeEnum contentType) {
        Object sample = sample(contentType);
        String json = MessageContents.toJson(sample, contentType);
        Object parsed = MessageContents.parse(json, contentType);
        assertEquals(JSON.toJSONString(sample instanceof String s ? s : sample),
                JSON.toJSONString(parsed instanceof String s ? s : parsed));

        Message message = new Message();
        message.setId("c-" + contentType.getType());
        message.setFrom("a");
        message.setTo("b");
        message.setContentType(contentType.getType());
        message.setContent(json);
        Packet decoded = writeThenRead(packet(MessageTypeEnum.ONE_2_ONE.getType(), message));
        assertEquals(contentType.getType(), decoded.getMessage().getContentType());
        assertEquals(json, decoded.getMessage().getContent());
    }

    @Test
    void loginIdentityAndRouteStayConsistentAcrossCodec() {
        long now = System.currentTimeMillis();
        String appKey = "shop";
        String identity = "user:100";
        String secret = "sec";
        assertTrue(LoginSignatureUtil.isCreateTimeValid(now, now));
        String sign = MD5Util.md5(LoginSignatureUtil.buildRaw(appKey, identity, now, secret));

        LoginContent login = new LoginContent();
        login.setAppKey(appKey);
        login.setIdentity(identity);
        login.setCreateTime(now);
        login.setSignature(sign);
        login.setSn("device-sn");

        String body = MessageContents.toJson(login, MessageContentTypeEnum.LOGIN_REQUEST_CONTENT);
        Message message = new Message();
        message.setId("login-1");
        message.setFrom(identity);
        message.setTo(appKey);
        message.setContentType(MessageContentTypeEnum.LOGIN_REQUEST_CONTENT.getType());
        message.setContent(body);

        Packet decoded = writeThenRead(packet(MessageTypeEnum.LOGIN.getType(), message));
        LoginContent back = (LoginContent) MessageContents.parse(decoded.getMessage());
        assertEquals(sign, back.getSignature());
        assertEquals(sign, MD5Util.md5(LoginSignatureUtil.buildRaw(back.getAppKey(), back.getIdentity(), back.getCreateTime(), secret)));

        String combo = IdentityUtil.generalComboIdentity(back.getAppKey(), back.getIdentity(), NumberConstant.NUMBER_1);
        assertEquals(identity, IdentityUtil.revertIdentity(combo));
        String route = ImRouteCodec.encode("10.0.0.8:9000", 7L, now);
        assertEquals("10.0.0.8:9000", ImRouteCodec.nodeId(route));
        assertEquals(7L, ImRouteCodec.epoch(route));
        assertEquals(now, ImRouteCodec.lastLoginTime(route));
        assertEquals(0L, ImRouteCodec.lastLoginTime(ImRouteCodec.encode("h:1", 3L)));
    }

    @Test
    void groupChatCarriesAtAndRefThroughPacket() {
        Message message = new Message();
        message.setId("g1");
        message.setFrom("app:owner:1");
        message.setTo("group-9");
        message.setContentType(MessageContentTypeEnum.IMAGE_CONTENT.getType());
        message.setContent(MessageContents.toJson(new ImageContent("https://cdn/a.png", "a.png", "image/png", 12), MessageContentTypeEnum.IMAGE_CONTENT));
        message.setAt(List.of("app:u1:1", "app:u2:2"));
        message.setRef(List.of("msg-prev"));
        Packet decoded = writeThenRead(packet(MessageTypeEnum.GROUP.getType(), message));
        assertEquals(List.of("app:u1:1", "app:u2:2"), decoded.getMessage().getAt());
        assertEquals(List.of("msg-prev"), decoded.getMessage().getRef());
        ImageContent image = (ImageContent) MessageContents.parse(decoded.getMessage());
        assertEquals("https://cdn/a.png", image.getUrl());
    }

    @Test
    void badMagicAndShortBodyRejected() {
        ByteBuf bad = Unpooled.buffer();
        bad.writeBytes(new byte[MessageConstant.PACKET_BASE_LENGTH]);
        assertThrows(MessageException.class, () -> PacketReaderWriterUtil.readByteBuf2Packet(bad));
        bad.release();

        Packet packet = packet(MessageTypeEnum.PING_PONG.getType(), ping());
        ByteBuf out = Unpooled.buffer();
        PacketReaderWriterUtil.writePacketInByteBuf(packet, out);
        ByteBuf cut = out.readSlice(out.readableBytes() - 1);
        ByteBuf copy = Unpooled.copiedBuffer(cut);
        assertThrows(MessageException.class, () -> PacketReaderWriterUtil.readByteBuf2Packet(copy));
        out.release();
        copy.release();
    }

    private static Packet writeThenRead(Packet packet) {
        ByteBuf buf = Unpooled.buffer();
        PacketReaderWriterUtil.writePacketInByteBuf(packet, buf);
        try {
            return PacketReaderWriterUtil.readByteBuf2Packet(buf);
        } finally {
            buf.release();
        }
    }

    private static Packet packet(byte messageType, Message message) {
        return new Packet((byte) 1, (byte) 1, 10001L, NumberConstant.NUMBER_1, NumberConstant.NUMBER_2,
                Encrypt.SymmetryEncrypt.NONE.getValue(), NumberConstant.NUMBER_2, messageType, (byte) 0, message);
    }

    private static Message ping() {
        Message message = new Message();
        message.setContent("x");
        message.setContentType(MessageContentTypeEnum.PING_PONG_CONTENT.getType());
        return message;
    }

    private static Object sample(MessageContentTypeEnum type) {
        Class<?> clazz = type.getContentClass();
        if (clazz == String.class) {
            return "text-" + type.getType();
        }
        if (List.class.isAssignableFrom(clazz)) {
            return List.of("id-1", "id-2");
        }
        if (clazz == ImageContent.class) {
            return new ImageContent("https://cdn/p.png", "p.png", "image/png", 3);
        }
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(type.name(), e);
        }
    }
}
