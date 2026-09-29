package com.ouyunc.message.safety;

import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.PacketReaderWriterUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感词过滤与私聊包编解码联动：先掩码再入包，解码后正文已脱敏。
 */
class ChatSafetyLinkageTest {

    @Test
    void maskedTextSurvivesPacketRoundTrip() {
        SensitiveWordAcAutomaton filter = new SensitiveWordAcAutomaton(List.of("违禁", "BadWord"));
        String raw = "这里有违禁内容和badword";
        List<String> hits = filter.findAll(raw);
        assertTrue(hits.contains("违禁"));
        assertTrue(hits.contains("badword"));
        String masked = filter.mask(raw, hits, "*");

        Message message = new Message();
        message.setId("safe-1");
        message.setFrom("a");
        message.setTo("b");
        message.setContentType(MessageContentTypeEnum.PING_PONG_CONTENT.getType());
        message.setContent(masked);

        Packet packet = new Packet((byte) 1, (byte) 1, 9L, NumberConstant.NUMBER_1, NumberConstant.NUMBER_2,
                Encrypt.SymmetryEncrypt.NONE.getValue(), NumberConstant.NUMBER_2,
                MessageTypeEnum.ONE_2_ONE.getType(), (byte) 0, message);
        ByteBuf buf = Unpooled.buffer();
        PacketReaderWriterUtil.writePacketInByteBuf(packet, buf);
        Packet decoded = PacketReaderWriterUtil.readByteBuf2Packet(buf);
        buf.release();

        assertEquals(masked, decoded.getMessage().getContent());
        assertTrue(filter.findAll(decoded.getMessage().getContent()).isEmpty());
    }
}
