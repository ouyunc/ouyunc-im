package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.AtTargetEnum;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.exception.ExceptionReporter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 消息内容列表规范化：统一处理群聊 @ 目标和引用消息 ID。 */
public final class MessageContentNormalizer {

    private static final Logger log = LoggerFactory.getLogger(MessageContentNormalizer.class);

    private MessageContentNormalizer() {
    }

    public static boolean containsAtAll(List<String> rawAt) {
        if (CollectionUtils.isEmpty(rawAt)) {
            return false;
        }
        for (String item : rawAt) {
            if (AtTargetEnum.isAtAll(StringUtils.trim(item))) {
                return true;
            }
        }
        return false;
    }

    public static List<String> explicitAtMemberIds(List<String> rawAt) {
        if (CollectionUtils.isEmpty(rawAt)) {
            return List.of();
        }
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        for (String item : rawAt) {
            if (StringUtils.isBlank(item)) {
                continue;
            }
            String normalized = StringUtils.trim(item);
            if (!AtTargetEnum.isAtAll(normalized)) {
                identities.add(normalized);
            }
        }
        return new ArrayList<>(identities);
    }

    public static List<String> normalizeAt(List<String> rawAt, Set<String> confirmedMemberIds) {
        if (CollectionUtils.isEmpty(rawAt)) {
            return null;
        }
        Set<String> members = confirmedMemberIds == null ? Collections.emptySet() : confirmedMemberIds;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String item : rawAt) {
            if (StringUtils.isBlank(item)) {
                continue;
            }
            String identity = StringUtils.trim(item);
            if (AtTargetEnum.isAtAll(identity)) {
                normalized.add(AtTargetEnum.AT_ALL.getValue());
            } else if (!members.contains(identity)) {
                throw new IllegalArgumentException("at 成员不在群内: " + identity);
            } else {
                normalized.add(identity);
            }
        }
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.size() > MessageConstant.MAX_AT_TARGET_COUNT) {
            throw new IllegalArgumentException("at 人数超过上限 " + MessageConstant.MAX_AT_TARGET_COUNT);
        }
        return new ArrayList<>(normalized);
    }

    public static Set<String> resolveAtDeliveryTargets(List<String> atList,
                                                       Set<String> groupMembersWithoutSender) {
        if (CollectionUtils.isEmpty(atList) || CollectionUtils.isEmpty(groupMembersWithoutSender)) {
            return Collections.emptySet();
        }
        if (atList.stream().anyMatch(AtTargetEnum::isAtAll)) {
            return new HashSet<>(groupMembersWithoutSender);
        }
        Set<String> targets = new HashSet<>();
        for (String member : atList) {
            if (groupMembersWithoutSender.contains(member)) {
                targets.add(member);
            }
        }
        return targets;
    }

    /** 单聊等非群场景忽略客户端误传的 at。 */
    public static void clearAt(Message message) {
        if (message != null && CollectionUtils.isNotEmpty(message.getAt())) {
            message.setAt(null);
        }
    }

    public static boolean normalizeReferencesOrReject(Packet packet) {
        Message message = packet.getMessage();
        if (message == null || CollectionUtils.isEmpty(message.getRef())) {
            return true;
        }
        try {
            message.setRef(normalizeReferences(message.getRef()));
            return true;
        } catch (IllegalArgumentException error) {
            log.warn("消息引用校验失败: {} | packet={}", error.getMessage(), packet);
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.MESSAGE_REF_INVALID_ERROR,
                    error.getMessage(), "MessageContentNormalizer.normalizeReferencesOrReject", packet);
            return false;
        }
    }

    public static List<String> normalizeReferences(List<String> rawReferences) {
        if (CollectionUtils.isEmpty(rawReferences)) {
            return null;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String item : rawReferences) {
            if (StringUtils.isBlank(item)) {
                continue;
            }
            String packetId = StringUtils.trim(item);
            if (!StringUtils.isNumeric(packetId) || MessageConstant.ZERO_STR.equals(packetId)) {
                throw new IllegalArgumentException("引用消息 id 无效: " + packetId);
            }
            normalized.add(packetId);
        }
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.size() > MessageConstant.MAX_REF_COUNT) {
            throw new IllegalArgumentException("引用消息超过上限 " + MessageConstant.MAX_REF_COUNT);
        }
        return new ArrayList<>(normalized);
    }
}
