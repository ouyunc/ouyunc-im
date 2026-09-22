package com.ouyunc.repository.support;

import com.ouyunc.base.packet.Packet;

import java.util.Collections;
import java.util.List;

/**
 * 消息查询结果，明确区分权威不存在与基础设施暂不可用。
 */
public record PacketQueryResult(Status status, List<Packet> packets, List<Long> missingIds) {

    public PacketQueryResult {
        packets = packets == null ? Collections.emptyList() : List.copyOf(packets);
        missingIds = missingIds == null ? Collections.emptyList() : List.copyOf(missingIds);
    }

    public enum Status {
        FOUND,
        NOT_FOUND,
        UNAVAILABLE,
        PARTIAL
    }

    public boolean isUnavailable() {
        return status == Status.UNAVAILABLE || status == Status.PARTIAL;
    }
}
