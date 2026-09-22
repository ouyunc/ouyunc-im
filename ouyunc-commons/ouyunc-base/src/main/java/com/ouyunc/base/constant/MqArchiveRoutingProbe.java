package com.ouyunc.base.constant;

import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;

/**
 * 核对：领域包不走 SAVE；SAVE key=appKey:messageId；领域分区键带 appKey。
 */
public final class MqArchiveRoutingProbe {

    private MqArchiveRoutingProbe() {
    }

    public static void main(String[] args) {
        Packet chat = packet(MessageTypeEnum.ONE_2_ONE.getType(), 1, "ak", "u2", "u1", null, "m-1");
        Packet read = packet(MessageTypeEnum.ONE_2_ONE.getType(), MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType(), "ak", "u2", "u1", null, "m-r");
        Packet withdraw = packet(MessageTypeEnum.GROUP.getType(), MessageContentTypeEnum.WITHDRAW_CONTENT.getType(), "ak", "g1", "u1", null, "m-w");
        Packet friend = packet(MessageTypeEnum.ONE_2_ONE_FRIEND_REQUEST_JOIN.getType(), 1, "ak", "u2", "u1", null, "m-f");
        Packet groupReq = packet(MessageTypeEnum.GROUP_REQUEST_JOIN.getType(), 1, "ak", "g9", "u1", null, "m-g");
        Packet cs = packet(MessageTypeEnum.CUSTOMER_SERVICE.getType(), 1, "ak", "svc", "user", "t-1", "m-cs");

        assertFalse(MqArchiveRouting.usesDomainConfirmOnly(chat), "chat 应走 SAVE");
        assertTrue(MqArchiveRouting.usesDomainConfirmOnly(read), "已读只走领域 topic");
        assertTrue(MqArchiveRouting.usesDomainConfirmOnly(withdraw), "撤回只走领域 topic");
        assertTrue(MqArchiveRouting.usesDomainConfirmOnly(friend), "好友请求只走领域 topic");
        assertTrue(MqArchiveRouting.usesDomainConfirmOnly(groupReq), "群请求只走领域 topic");
        assertFalse(MqArchiveRouting.usesDomainConfirmOnly(cs), "客服聊天应走 SAVE");

        assertEquals("ak:m-1", MqArchiveRouting.archiveKey(chat), "SAVE key=appKey:messageId");
        assertEquals("ak:m-cs", MqArchiveRouting.archiveKey(cs), "客服 SAVE key=appKey:messageId");
        String chatSessionKey = MqArchiveRouting.partitionKey(chat);
        assertTrue(chatSessionKey != null && chatSessionKey.startsWith("ak:"), "领域/会话 key 应带 appKey");
        assertTrue(chatSessionKey.contains("u1") && chatSessionKey.contains("u2"), "会话 key 应含双方");
        assertEquals("ak:g1", MqArchiveRouting.partitionKey(withdraw), "群撤回 key=appKey:groupId");
        assertEquals("ak:g9", MqArchiveRouting.partitionKey(groupReq), "群请求 key=appKey:groupId");
        assertEquals("ak:t-1", MqArchiveRouting.partitionKey(cs), "客服领域 key=appKey:ticketId");
        System.out.println("MqArchiveRoutingProbe ok");
    }

    private static Packet packet(byte messageType, int contentType, String appKey, String to, String from,
                                 String correlationId, String messageId) {
        Packet packet = new Packet();
        packet.setMessageType(messageType);
        Message message = new Message();
        message.setId(messageId);
        message.setFrom(from);
        message.setTo(to);
        message.setContentType(contentType);
        message.setCorrelationId(correlationId);
        Metadata metadata = new Metadata();
        metadata.setAppKey(appKey);
        message.setMetadata(metadata);
        packet.setMessage(message);
        return packet;
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    private static void assertFalse(boolean cond, String msg) {
        assertTrue(!cond, msg);
    }

    private static void assertEquals(String expected, String actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + " expected=" + expected + " actual=" + actual);
        }
    }
}
