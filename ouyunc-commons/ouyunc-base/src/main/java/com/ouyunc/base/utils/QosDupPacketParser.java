package com.ouyunc.base.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * QOS_DUP 正文解析：只抽取协议头 + 业务 Message 标量/列表，禁止 {@code Packet.class} 全图反序列化。
 * <p>客户端 Metadata（clusterForwardMode/target/fanout/qosOwnerToken 等）一律丢弃，由入站通道元数据覆盖。</p>
 */
public final class QosDupPacketParser {

    private static final Logger log = LoggerFactory.getLogger(QosDupPacketParser.class);

    private QosDupPacketParser() {
    }

    /**
     * @param json QOS_DUP {@code message.content}
     * @return 不含客户端 Metadata 的业务包；解析失败返回 null
     */
    public static Packet parse(String json) {
        if (StringUtils.isBlank(json) || json.length() > MessageConstant.MAX_MESSAGE_CONTENT_LENGTH) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(json);
            if (root == null) {
                return null;
            }
            Packet packet = new Packet();
            packet.setProtocol(root.getByteValue(Packet.Fields.PROTOCOL));
            packet.setProtocolVersion(root.getByteValue(Packet.Fields.PROTOCOL_VERSION));
            packet.setPacketId(readPacketId(root));
            packet.setDeviceType(root.getByteValue(Packet.Fields.DEVICE_TYPE));
            packet.setNetworkType(root.getByteValue(Packet.Fields.NETWORK_TYPE));
            packet.setEncryptType(root.getByteValue(Packet.Fields.ENCRYPT_TYPE));
            packet.setSerializeAlgorithm(root.getByteValue(Packet.Fields.SERIALIZE_ALGORITHM));
            packet.setMessageType(root.getByteValue(Packet.Fields.MESSAGE_TYPE));
            packet.setRetain(root.getByteValue(Packet.Fields.RETAIN));
            JSONObject msgObj = root.getJSONObject(Packet.Fields.MESSAGE);
            if (msgObj == null) {
                return null;
            }
            Message message = new Message();
            message.setId(msgObj.getString(Message.Fields.ID));
            message.setFrom(msgObj.getString(Message.Fields.FROM));
            message.setFromType(msgObj.getIntValue(Message.Fields.FROM_TYPE));
            message.setTo(msgObj.getString(Message.Fields.TO));
            message.setToType(msgObj.getIntValue(Message.Fields.TO_TYPE));
            message.setContentType(msgObj.getIntValue(Message.Fields.CONTENT_TYPE));
            String content = msgObj.getString(Message.Fields.CONTENT);
            if (content != null && content.length() > MessageConstant.MAX_MESSAGE_CONTENT_LENGTH) {
                log.warn("QOS_DUP 内嵌 content 超长, len={}", content.length());
                return null;
            }
            message.setContent(content);
            message.setAt(readStringList(msgObj.getJSONArray(Message.Fields.AT)));
            message.setRef(readStringList(msgObj.getJSONArray(Message.Fields.REF)));
            message.setExtra(msgObj.getString(Message.Fields.EXTRA));
            message.setQos(msgObj.getIntValue(Message.Fields.QOS));
            message.setCreateTime(msgObj.getLongValue(Message.Fields.CREATE_TIME));
            message.setCorrelationId(msgObj.getString(Message.Fields.CORRELATION_ID));
            packet.setMessage(message);
            if (packet.getPacketId() <= 0L && StringUtils.isBlank(message.getId())) {
                return null;
            }
            return packet;
        } catch (Exception e) {
            log.warn("QOS_DUP 正文解析失败: {}", e.toString());
            return null;
        }
    }

    private static long readPacketId(JSONObject root) {
        Object raw = root.get(Packet.Fields.PACKET_ID);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String str && StringUtils.isNotBlank(str)) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return root.getLongValue(Packet.Fields.PACKET_ID);
    }

    private static List<String> readStringList(JSONArray array) {
        if (array == null || array.isEmpty()) {
            return null;
        }
        List<String> values = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String item = array.getString(i);
            if (StringUtils.isNotBlank(item)) {
                values.add(item);
            }
        }
        return values.isEmpty() ? null : values;
    }
}
