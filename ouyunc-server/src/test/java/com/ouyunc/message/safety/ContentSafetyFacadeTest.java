package com.ouyunc.message.safety;

import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.model.ContentSafetyResult;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话控制通知不得进入文本审核。不启动词库，也不访问腾讯云。
 */
class ContentSafetyFacadeTest {

    @Test
    void csSessionEventContentSkipsTextModeration() {
        Message message = new Message(
                "assigned-1",
                "cs-system",
                "agent-1",
                MessageContentTypeEnum.CS_SESSION_EVENT_CONTENT.getType(),
                "{\"event\":\"cs.session.assigned\",\"ticketId\":\"100\"}",
                1L);
        Packet packet = new Packet(
                (byte) 1, (byte) 1, 2L, (byte) 1, (byte) 0, (byte) 0, (byte) 2,
                MessageTypeEnum.SERVER_NOTIFY.getType(),
                message);
        assertTrue(ContentSafetyFacade.check(packet).isPassed());
    }
}
