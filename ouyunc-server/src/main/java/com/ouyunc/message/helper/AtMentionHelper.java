package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.AtTargetEnum;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 群聊 @ 列表校验与投递目标解析（仅群消息使用 message.at）。
 */
public final class AtMentionHelper {

    private AtMentionHelper() {
    }

    /**
     * 去重、校验成员/@all，超过 {@link MessageConstant#MAX_AT_TARGET_COUNT} 时抛异常。
     */
    public static List<String> normalizeAndValidate(List<String> rawAt, Set<String> groupMemberIds) {
        if (CollectionUtils.isEmpty(rawAt)) {
            return null;
        }
        if (groupMemberIds == null) {
            groupMemberIds = Collections.emptySet();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String item : rawAt) {
            if (StringUtils.isBlank(item)) {
                continue;
            }
            String trimmed = StringUtils.trim(item);
            if (AtTargetEnum.isAtAll(trimmed)) {
                normalized.add(AtTargetEnum.AT_ALL.getValue());
                continue;
            }
            if (!groupMemberIds.contains(trimmed)) {
                throw new IllegalArgumentException("at 成员不在群内: " + trimmed);
            }
            normalized.add(trimmed);
        }
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.size() > MessageConstant.MAX_AT_TARGET_COUNT) {
            throw new IllegalArgumentException("at 人数超过上限 " + MessageConstant.MAX_AT_TARGET_COUNT);
        }
        return new ArrayList<>(normalized);
    }

    /**
     * 解析 @ 推送目标（不含发送方；@all 展开为全部群成员）。
     */
    public static Set<String> resolveDeliveryTargets(List<String> atList, Set<String> groupMembersWithoutSender) {
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

    /** 单聊等非群场景：忽略客户端误传的 at */
    public static void clearAtIfPresent(com.ouyunc.base.packet.message.Message message) {
        if (message != null && CollectionUtils.isNotEmpty(message.getAt())) {
            message.setAt(null);
        }
    }
}
