/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ouyunc.base.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.util.AttributeKey;

import java.util.Base64;

import static io.netty.handler.codec.mqtt.MqttConstant.MIN_CLIENT_ID_LENGTH;

public final class MqttCodecUtil {

    private static final char[] TOPIC_WILDCARDS = {'#', '+'};

    private static final AttributeKey<MqttVersion> MQTT_VERSION_KEY = AttributeKey.valueOf("NETTY_CODEC_MQTT_VERSION");
    private MqttCodecUtil() { }
    public static MqttVersion getMqttVersion(ChannelHandlerContext ctx) {
        return ctx.channel().attr(MQTT_VERSION_KEY).get();
    }
    public static MqttVersion getMqttVersion(byte protocolVersion) {
       if (MqttVersion.MQTT_3_1_1.protocolLevel() == protocolVersion) {
           return MqttVersion.MQTT_3_1_1;
       }else if (MqttVersion.MQTT_3_1.protocolLevel() == protocolVersion) {
           return MqttVersion.MQTT_3_1;
       }else if (MqttVersion.MQTT_5.protocolLevel() == protocolVersion) {
           return MqttVersion.MQTT_5;
       }
       return null;
    }
    public static String encode(MqttVersion mqttVersion, MqttMessage mqttMessage) {
        ByteBuf byteBuf = MqttEncoderUtil.INSTANCE.doEncode(mqttVersion, mqttMessage);
        byte[] mqttMessageBytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(mqttMessageBytes);
        byteBuf.release();
        return Base64.getEncoder().encodeToString(mqttMessageBytes);
    }
    public static MqttMessage decode(MqttVersion mqttVersion, String mqttMessageBase64Content) {
        byte[] mqttMessageBytes = Base64.getDecoder().decode(mqttMessageBase64Content);
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        buffer.writeBytes(mqttMessageBytes);
        MqttMessage mqttMessage = MqttDecoderUtil.INSTANCE.doDecode(mqttVersion, buffer);
        buffer.release();
        return mqttMessage;
    }

    public static boolean isValidPublishTopicName(String topicName) {
        // publish topic name must not contain any wildcard
        for (char c : TOPIC_WILDCARDS) {
            if (topicName.indexOf(c) >= 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidMessageId(int messageId) {
        return messageId != 0;
    }

    public static boolean isValidClientId(MqttVersion mqttVersion, int maxClientIdLength, String clientId) {
        if (mqttVersion == MqttVersion.MQTT_3_1) {
            return clientId != null && clientId.length() >= MIN_CLIENT_ID_LENGTH &&
                clientId.length() <= maxClientIdLength;
        }
        if (mqttVersion == MqttVersion.MQTT_3_1_1 || mqttVersion == MqttVersion.MQTT_5) {
            // In 3.1.3.1 Client Identifier of MQTT 3.1.1 and 5.0 specifications, The Server MAY allow ClientId’s
            // that contain more than 23 encoded bytes. And, The Server MAY allow zero-length ClientId.
            return clientId != null;
        }
        throw new IllegalArgumentException(mqttVersion + " is unknown mqtt version");
    }

    public static MqttFixedHeader validateFixedHeader(MqttVersion mqttVersion, MqttFixedHeader mqttFixedHeader) {
        switch (mqttFixedHeader.messageType()) {
            case PUBREL:
            case SUBSCRIBE:
            case UNSUBSCRIBE:
                if (mqttFixedHeader.qosLevel() != MqttQoS.AT_LEAST_ONCE) {
                    throw new DecoderException(mqttFixedHeader.messageType().name() + " message must have QoS 1");
                }
                return mqttFixedHeader;
            case AUTH:
                if (mqttVersion != MqttVersion.MQTT_5) {
                    throw new DecoderException("AUTH message requires at least MQTT 5");
                }
                return mqttFixedHeader;
            default:
                return mqttFixedHeader;
        }
    }

    public static MqttFixedHeader resetUnusedFields(MqttFixedHeader mqttFixedHeader) {
        switch (mqttFixedHeader.messageType()) {
            case CONNECT:
            case CONNACK:
            case PUBACK:
            case PUBREC:
            case PUBCOMP:
            case SUBACK:
            case UNSUBACK:
            case PINGREQ:
            case PINGRESP:
            case DISCONNECT:
                if (mqttFixedHeader.isDup() ||
                        mqttFixedHeader.qosLevel() != MqttQoS.AT_MOST_ONCE ||
                        mqttFixedHeader.isRetain()) {
                    return new MqttFixedHeader(
                            mqttFixedHeader.messageType(),
                            false,
                            MqttQoS.AT_MOST_ONCE,
                            false,
                            mqttFixedHeader.remainingLength());
                }
                return mqttFixedHeader;
            case PUBREL:
            case SUBSCRIBE:
            case UNSUBSCRIBE:
                if (mqttFixedHeader.isRetain()) {
                    return new MqttFixedHeader(
                            mqttFixedHeader.messageType(),
                            mqttFixedHeader.isDup(),
                            mqttFixedHeader.qosLevel(),
                            false,
                            mqttFixedHeader.remainingLength());
                }
                return mqttFixedHeader;
            default:
                return mqttFixedHeader;
        }
    }


}
