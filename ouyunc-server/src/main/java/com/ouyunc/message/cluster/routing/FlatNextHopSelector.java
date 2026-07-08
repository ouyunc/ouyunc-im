package com.ouyunc.message.cluster.routing;

import com.ouyunc.base.model.RoutingTable;
import com.ouyunc.message.context.MessageServerContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * flat 模式：在候选集中按 active 优先、SYN miss 次数升序选取。
 */
public final class FlatNextHopSelector implements ClusterNextHopSelector {

    public static final FlatNextHopSelector INSTANCE = new FlatNextHopSelector();

    private FlatNextHopSelector() {
    }

    @Override
    public String select(String targetServerAddress, List<RoutingTable> routingTables, Set<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        List<String> ordered = new ArrayList<>(candidates);
        ordered.sort(buildComparator());
        return ordered.getFirst();
    }

    static Comparator<String> buildComparator() {
        return Comparator
                .comparing((String address) -> !MessageServerContext.clusterActiveServerRegistryTableCache.asMap().containsKey(address))
                .thenComparingInt(FlatNextHopSelector::missAckTimes)
                .thenComparing(String::compareTo);
    }

    private static int missAckTimes(String address) {
        AtomicInteger counter = MessageServerContext.clusterClientMissAckTimesCache.get(address);
        return counter == null ? 0 : counter.get();
    }
}
