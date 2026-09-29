package com.ouyunc.base.utils;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.exception.MessageException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上线门禁：协议魔数、登录时钟窗、身份组合、会话号，不依赖外部中间件。
 */
class ReleaseGateUtilTest {

    @Test
    void magicBytesMatchAsciiOuyunc() {
        assertArrayEquals("OUYUNC".getBytes(StandardCharsets.US_ASCII), MessageConstant.PACKET_MAGIC_BYTES);
        assertTrue(PacketMagicUtil.isPacketMagic(MessageConstant.PACKET_MAGIC_BYTES));
        assertFalse(PacketMagicUtil.isPacketMagic(null));
        assertFalse(PacketMagicUtil.isPacketMagic(new byte[]{1, 2, 3}));
    }

    @Test
    void protocolPeekDoesNotAdvanceReader() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes(MessageConstant.PACKET_MAGIC_BYTES);
        buf.writeByte(0x02);
        int before = buf.readerIndex();
        assertTrue(PacketMagicUtil.matchesPacketProtocol(buf, (byte) 0x02));
        assertFalse(PacketMagicUtil.matchesPacketProtocol(buf, (byte) 0x01));
        assertEquals(before, buf.readerIndex());
        buf.release();
    }

    @Test
    void loginCreateTimeSkewWindow() {
        long now = 1_700_000_000_000L;
        assertTrue(LoginSignatureUtil.isCreateTimeValid(now, now));
        assertTrue(LoginSignatureUtil.isCreateTimeValid(now - MessageConstant.LOGIN_SIGNATURE_CREATE_TIME_SKEW_MS, now));
        assertFalse(LoginSignatureUtil.isCreateTimeValid(0L, now));
        assertFalse(LoginSignatureUtil.isCreateTimeValid(now - MessageConstant.LOGIN_SIGNATURE_CREATE_TIME_SKEW_MS - 1, now));
    }

    @Test
    void loginRawMatchesDocumentedFormat() {
        assertEquals("ak&user:1&100_secret", LoginSignatureUtil.buildRaw("ak", "user:1", 100L, "secret"));
        assertEquals(MD5Util.md5("ak&id&1_sec"), MD5Util.md5(LoginSignatureUtil.buildRaw("ak", "id", 1L, "sec")));
    }

    @Test
    void comboIdentityKeepsColonInsideIdentity() {
        String combo = IdentityUtil.generalComboIdentity("app", "user:room", (byte) 2);
        assertEquals("app", IdentityUtil.revertAppKey(combo));
        assertEquals("user:room", IdentityUtil.revertIdentity(combo));
        assertEquals((byte) 2, IdentityUtil.revertDeviceType(combo));
    }

    @Test
    void badComboRejected() {
        assertThrows(MessageException.class, () -> IdentityUtil.revertIdentity("onlyone"));
        assertThrows(MessageException.class, () -> IdentityUtil.revertDeviceType("app:id:not-byte"));
        assertThrows(MessageException.class, () -> IdentityUtil.sessionId(null, "b"));
    }

    @Test
    void sessionIdIsOrderIndependent() {
        assertEquals(IdentityUtil.sessionId("b", "a"), IdentityUtil.sessionId("a", "b"));
        assertTrue(IdentityUtil.sessionId("a", "b").startsWith("b:"));
    }
}
