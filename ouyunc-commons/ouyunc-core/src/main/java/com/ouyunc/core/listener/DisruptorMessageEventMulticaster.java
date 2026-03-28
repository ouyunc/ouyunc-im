package com.ouyunc.core.listener;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.EventHandlerGroup;
import com.ouyunc.base.constant.enums.EventRingEnum;
import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.metrics.DisruptorListenerExecSnapshot;
import com.ouyunc.core.listener.metrics.DisruptorRingMetrics;
import com.ouyunc.core.listener.metrics.ListenerExecutionStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 使用 Disruptor 原生 DSL 按监听器注解进行事件分发：
 * <ul>
 *   <li>相同 order：handleEventsWith(...) 并行执行</li>
 *   <li>不同 order：then(...) 串行屏障</li>
 *   <li>按 ring 将监听器划分到不同 Disruptor 实例</li>
 * </ul>
 */
public class DisruptorMessageEventMulticaster extends AbstractMessageEventMulticaster {

    private final ConcurrentMap<EventType, EventDispatchRoute> dispatchRoutes = new ConcurrentHashMap<>();
    private final ConcurrentMap<EventType, Map<EventRingEnum, List<MessageEventListener<MessageEvent>>>> listenersByRingCache = new ConcurrentHashMap<>();

    @Override
    public void addMessageListener(MessageEventListener<MessageEvent> listener) {
        super.addMessageListener(listener);
        if (listener != null && listener.type() != null) {
            invalidateDispatchersForEventType(listener.type());
        }
    }

    @Override
    public void removeMessageListener(MessageEventListener<MessageEvent> listener) {
        super.removeMessageListener(listener);
        if (listener != null && listener.type() != null) {
            invalidateDispatchersForEventType(listener.type());
        }
    }

    @Override
    public void removeMessageListener(MessageEvent event) {
        EventType eventType = event == null ? null : event.getType();
        super.removeMessageListener(event);
        if (eventType != null) {
            invalidateDispatchersForEventType(eventType);
        }
    }

    @Override
    public void removeAllMessageListeners() {
        super.removeAllMessageListeners();
        listenersByRingCache.clear();
        dispatchRoutes.forEach((eventType, route) -> route.shutdown());
        dispatchRoutes.clear();
    }

    @Override
    public List<DisruptorRingMetrics> snapshotDisruptorMetrics() {
        if (dispatchRoutes.isEmpty()) {
            return List.of();
        }
        List<DisruptorRingMetrics> list = new ArrayList<>();
        dispatchRoutes.values().forEach(route -> route.appendRingMetrics(list));
        return Collections.unmodifiableList(list);
    }

    @Override
    public void multicastEvent(MessageEvent event, boolean async) {
        if (event == null) {
            return;
        }
        if (!async) {
            dispatchToListeners(event);
            return;
        }
        EventType eventType = event.getType();
        if (eventType == null) {
            return;
        }
        EventDispatchRoute route = dispatchRoutes.get(eventType);
        if (route == null) {
            Map<EventRingEnum, List<MessageEventListener<MessageEvent>>> listenersByRing = groupListenersByRing(event);
            EventDispatchRoute newRoute = new EventDispatchRoute(eventType, listenersByRing);
            EventDispatchRoute oldRoute = dispatchRoutes.putIfAbsent(eventType, newRoute);
            route = oldRoute == null ? newRoute : oldRoute;
        }
        route.publish(event, this);
    }

    private Map<EventRingEnum, List<MessageEventListener<MessageEvent>>> groupListenersByRing(MessageEvent event) {
        if (event == null || event.getType() == null) {
            return Map.of();
        }
        EventType eventType = event.getType();
        Map<EventRingEnum, List<MessageEventListener<MessageEvent>>> cached = listenersByRingCache.get(eventType);
        if (cached != null) {
            return cached;
        }
        List<MessageEventListener<MessageEvent>> listeners = getOrderedListeners(event);
        if (listeners.isEmpty()) {
            listenersByRingCache.put(eventType, Map.of());
            return Map.of();
        }
        Map<EventRingEnum, List<MessageEventListener<MessageEvent>>> grouped = new EnumMap<>(EventRingEnum.class);
        for (MessageEventListener<MessageEvent> listener : listeners) {
            EventRingEnum ring = resolveListenerRing(listener);
            grouped.computeIfAbsent(ring, k -> new ArrayList<>()).add(listener);
        }
        Map<EventRingEnum, List<MessageEventListener<MessageEvent>>> immutableGrouped = new EnumMap<>(EventRingEnum.class);
        grouped.forEach((ring, ringListeners) -> immutableGrouped.put(ring, List.copyOf(ringListeners)));
        Map<EventRingEnum, List<MessageEventListener<MessageEvent>>> unmodifiable = Map.copyOf(immutableGrouped);
        listenersByRingCache.put(eventType, unmodifiable);
        return unmodifiable;
    }

    private RingDispatcher buildDispatcher(EventType eventType, EventRingEnum ring, List<MessageEventListener<MessageEvent>> ringListeners) {
        ThreadFactory threadFactory = new NamedThreadFactory("event-disruptor-" + ring.name().toLowerCase() + "-" + eventType.getType());
        Disruptor<DisruptorEvent> disruptor = new Disruptor<>(
                DisruptorEvent.EVENT_FACTORY,
                ring.getBufferSize(),
                threadFactory,
                ring.getProducerType(),
                createWaitStrategy(ring)
        );
        Map<Integer, List<MessageEventListener<MessageEvent>>> byOrder = groupListenersByOrder(ringListeners);
        List<ListenerExecutionStats> handlerStats = new ArrayList<>();
        EventHandlerGroupBuilder groupBuilder = new EventHandlerGroupBuilder(disruptor, handlerStats);
        byOrder.forEach(groupBuilder::addStage);
        disruptor.start();
        RingBuffer<DisruptorEvent> ringBuffer = disruptor.getRingBuffer();
        log.info("Disruptor dispatcher initialized, eventType={}, ring={}, stages={}", eventType.getType(), ring, byOrder.size());
        return new RingDispatcher(disruptor, ringBuffer, handlerStats);
    }

    private Map<Integer, List<MessageEventListener<MessageEvent>>> groupListenersByOrder(List<MessageEventListener<MessageEvent>> listeners) {
        List<MessageEventListener<MessageEvent>> ordered = new ArrayList<>(listeners);
        ordered.sort(Comparator.comparingInt(this::resolveListenerOrder));
        Map<Integer, List<MessageEventListener<MessageEvent>>> grouped = new TreeMap<>();
        for (MessageEventListener<MessageEvent> listener : ordered) {
            grouped.computeIfAbsent(resolveListenerOrder(listener), ignored -> new ArrayList<>()).add(listener);
        }
        return grouped;
    }

    private final class ListenerHandler implements EventHandler<DisruptorEvent> {

        private final MessageEventListener<MessageEvent> listener;
        private final ListenerExecutionStats stats;

        private ListenerHandler(MessageEventListener<MessageEvent> listener, ListenerExecutionStats stats) {
            this.listener = listener;
            this.stats = stats;
        }

        @Override
        public void onEvent(DisruptorEvent holder, long sequence, boolean endOfBatch) {
            invokeListener(listener, holder.event, (elapsedNanos, error) -> stats.record(elapsedNanos, error));
        }
    }

    private final class EventHandlerGroupBuilder {

        private final Disruptor<DisruptorEvent> disruptor;
        private final List<ListenerExecutionStats> handlerStats;
        private EventHandlerGroup<DisruptorEvent> current;

        private EventHandlerGroupBuilder(Disruptor<DisruptorEvent> disruptor, List<ListenerExecutionStats> handlerStats) {
            this.disruptor = disruptor;
            this.handlerStats = handlerStats;
        }

        private void addStage(int order, List<MessageEventListener<MessageEvent>> listeners) {
            @SuppressWarnings("unchecked")
            EventHandler<DisruptorEvent>[] handlers = new EventHandler[listeners.size()];
            for (int i = 0; i < listeners.size(); i++) {
                MessageEventListener<MessageEvent> l = listeners.get(i);
                ListenerExecutionStats stats = new ListenerExecutionStats(l.getClass().getName(), order);
                handlerStats.add(stats);
                handlers[i] = new ListenerHandler(l, stats);
            }
            if (handlers.length == 0) {
                return;
            }
            if (current == null) {
                current = disruptor.handleEventsWith(handlers);
            } else {
                current = current.then(handlers);
            }
        }
    }

    private static final class DisruptorEvent {
        private static final EventFactory<DisruptorEvent> EVENT_FACTORY = DisruptorEvent::new;
        private static final EventTranslatorOneArg<DisruptorEvent, MessageEvent> TRANSLATOR =
                (holder, sequence, messageEvent) -> holder.event = messageEvent;
        private MessageEvent event;
    }

    private static final class RingDispatcher {
        private final Disruptor<DisruptorEvent> disruptor;
        private final RingBuffer<DisruptorEvent> ringBuffer;
        private final List<ListenerExecutionStats> listenerStats;
        private final LongAdder publishedEvents = new LongAdder();

        private RingDispatcher(
                Disruptor<DisruptorEvent> disruptor,
                RingBuffer<DisruptorEvent> ringBuffer,
                List<ListenerExecutionStats> listenerStats) {
            this.disruptor = disruptor;
            this.ringBuffer = ringBuffer;
            this.listenerStats = listenerStats;
        }

        private void publish(MessageEvent event) {
            publishedEvents.increment();
            ringBuffer.publishEvent(DisruptorEvent.TRANSLATOR, event);
        }

        private void shutdown() {
            disruptor.shutdown();
        }

        private DisruptorRingMetrics snapshot(EventType eventType, EventRingEnum ring) {
            long cursor = ringBuffer.getCursor();
            long minGating = ringBuffer.getMinimumGatingSequence();
            long pending = cursor >= minGating ? cursor - minGating : 0L;
            List<DisruptorListenerExecSnapshot> execSnapshots = new ArrayList<>(listenerStats.size());
            for (ListenerExecutionStats s : listenerStats) {
                execSnapshots.add(s.snapshot());
            }
            return new DisruptorRingMetrics(
                    String.valueOf(eventType.getType()),
                    ring.name(),
                    ringBuffer.getBufferSize(),
                    cursor,
                    minGating,
                    pending,
                    ringBuffer.remainingCapacity(),
                    publishedEvents.sum(),
                    disruptor.hasStarted(),
                    List.copyOf(execSnapshots)
            );
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {

        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

    private void invalidateDispatchersForEventType(EventType eventType) {
        listenersByRingCache.remove(eventType);
        EventDispatchRoute route = dispatchRoutes.remove(eventType);
        if (route != null) {
            route.shutdown();
        }
    }

    private static final class EventDispatchRoute {
        private final EventType eventType;
        private final EventRingEnum[] rings;
        private final List<MessageEventListener<MessageEvent>>[] listenersByRing;
        private final RingDispatcher[] dispatchers;

        @SuppressWarnings("unchecked")
        private EventDispatchRoute(EventType eventType, Map<EventRingEnum, List<MessageEventListener<MessageEvent>>> grouped) {
            this.eventType = eventType;
            this.rings = grouped.keySet().toArray(new EventRingEnum[0]);
            this.listenersByRing = new List[this.rings.length];
            this.dispatchers = new RingDispatcher[this.rings.length];
            for (int i = 0; i < this.rings.length; i++) {
                this.listenersByRing[i] = grouped.get(this.rings[i]);
            }
        }

        private void publish(MessageEvent event, DisruptorMessageEventMulticaster owner) {
            for (int i = 0; i < rings.length; i++) {
                RingDispatcher dispatcher = dispatchers[i];
                if (dispatcher == null) {
                    synchronized (this) {
                        dispatcher = dispatchers[i];
                        if (dispatcher == null) {
                            dispatcher = owner.buildDispatcher(eventType, rings[i], listenersByRing[i]);
                            dispatchers[i] = dispatcher;
                        }
                    }
                }
                dispatcher.publish(event);
            }
        }

        private void shutdown() {
            for (RingDispatcher dispatcher : dispatchers) {
                if (dispatcher != null) {
                    dispatcher.shutdown();
                }
            }
        }

        private void appendRingMetrics(List<DisruptorRingMetrics> out) {
            for (int i = 0; i < rings.length; i++) {
                RingDispatcher dispatcher = dispatchers[i];
                if (dispatcher != null) {
                    out.add(dispatcher.snapshot(eventType, rings[i]));
                }
            }
        }
    }

    private WaitStrategy createWaitStrategy(EventRingEnum ring) {
        if (ring.getWaitStrategyMode() == EventRingEnum.WaitStrategyMode.YIELDING) {
            return new YieldingWaitStrategy();
        }else if (ring.getWaitStrategyMode() == EventRingEnum.WaitStrategyMode.BUSY_SPIN_WAIT) {
            return new BusySpinWaitStrategy();
        }
        return new BlockingWaitStrategy();
    }
}
