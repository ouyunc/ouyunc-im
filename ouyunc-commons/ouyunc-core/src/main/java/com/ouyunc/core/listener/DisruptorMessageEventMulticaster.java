package com.ouyunc.core.listener;

import com.google.common.collect.Lists;
import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.EventHandlerGroup;
import com.ouyunc.base.constant.enums.EventRingEnum;
import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.metrics.DisruptorListenerExecSnapshot;
import com.ouyunc.core.listener.metrics.DisruptorRingMetrics;
import com.ouyunc.core.listener.metrics.ListenerExecutionStats;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 使用 Disruptor 原生 DSL 按监听器注解进行事件分发。
 * <p><b>异步发布心智模型</b>：根据 {@link MessageEvent#getType() 事件类型} 得到该类型涉及的 <b>一个或多个</b> 物理环，
 * 再向每个环投递同一条 {@link MessageEvent}。热路径为 {@code EventType → Set<RingDispatcher>} 一次查表，
 * 异常、发送失败和业务空闲事件使用 {@link RingBuffer#tryPublishEvent}，环满时丢弃并计数；登录等业务事件仍阻塞发布。
 * <ul>
 *   <li>每个 {@link EventRingEnum} 全进程唯一一个物理 Disruptor；环内只按 {@code order} 建链（跨事件类型混排）</li>
 *   <li>相同 order：{@code handleEventsWith(...)} 并行；不同 order：{@code then(...)} 串行屏障</li>
 *   <li>各 {@link EventHandler} 内按 {@code listener.type()} 与 {@code event.getType()} 过滤后再调用业务监听</li>
 * </ul>
 */
public class DisruptorMessageEventMulticaster extends AbstractMessageEventMulticaster {
    /**
     * 监听器事件执行器
     */
    private Executor taskExecutor;



    /** 每个物理环当前活跃的 Disruptor 派发器（懒创建；重建环时替换并 shutdown 旧实例） */
    private final ConcurrentMap<EventRingEnum, RingDispatcher> globalRingDispatchers = new ConcurrentHashMap<>();
    /**
     * 事件类型 → 该类型要投递的环派发器（可能多个）。缓存的是 {@link RingDispatcher} 引用；
     * 环被摘掉时整表清空，避免持有已关闭的实例。
     */
    private final ConcurrentMap<EventType, Set<RingDispatcher>> dispatchersByEventType = new ConcurrentHashMap<>();


    public DisruptorMessageEventMulticaster() {
    }

    public DisruptorMessageEventMulticaster(Executor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }


    /**
     * Return the current task executor for this multicaster.
     */
    protected Executor getTaskExecutor() {
        return this.taskExecutor;
    }
    /**
     * @Author fzx
     * @Description 设置任务执行器
     * @param taskExecutor
     * @return void
     */
    public void setTaskExecutor(Executor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    @Override
    public void addMessageListener(MessageEventListener<MessageEvent> listener) {
        List<RingDispatcher> retired;
        synchronized (this) {
            super.addMessageListener(listener);
            if (listener == null || listener.type() == null) {
                return;
            }
            retired = detachRing(resolveListenerRing(listener));
        }
        shutdownRetired(retired);
    }

    @Override
    public void removeMessageListener(MessageEventListener<MessageEvent> listener) {
        List<RingDispatcher> retired = List.of();
        synchronized (this) {
            EventRingEnum ring = listener != null ? resolveListenerRing(listener) : null;
            super.removeMessageListener(listener);
            if (ring != null) {
                retired = detachRing(ring);
            }
        }
        shutdownRetired(retired);
    }

    @Override
    public void removeMessageListener(MessageEvent event) {
        List<RingDispatcher> retired = List.of();
        synchronized (this) {
            EventType eventType = event == null ? null : event.getType();
            Set<EventRingEnum> touched = eventType == null ? Set.of() : ringsForEventType(eventType);
            super.removeMessageListener(event);
            if (eventType != null) {
                retired = detachRings(touched);
            }
        }
        shutdownRetired(retired);
    }

    @Override
    public void removeAllMessageListeners() {
        List<RingDispatcher> retired;
        synchronized (this) {
            retired = new ArrayList<>(globalRingDispatchers.values());
            for (RingDispatcher dispatcher : retired) {
                dispatcher.retire();
            }
            dispatchersByEventType.clear();
            globalRingDispatchers.clear();
            super.removeAllMessageListeners();
        }
        shutdownRetired(retired);
    }

    @Override
    public List<DisruptorRingMetrics> snapshotDisruptorMetrics() {
        if (globalRingDispatchers.isEmpty()) {
            return Lists.newArrayList();
        }
        List<DisruptorRingMetrics> list = new ArrayList<>();
        for (Map.Entry<EventRingEnum, RingDispatcher> e : globalRingDispatchers.entrySet()) {
            RingDispatcher d = e.getValue();
            if (d != null) {
                list.add(d.snapshot(e.getKey()));
            }
        }
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
        boolean bestEffort = MessageEventTypeEnum.EXCEPTION.equals(eventType)
                || MessageEventTypeEnum.EXCEPTION_PERSIST.equals(eventType)
                || MessageEventTypeEnum.SEND_FAIL.equals(eventType)
                || MessageEventTypeEnum.CLIENT_BUSINESS_SESSION_IDLE.equals(eventType);
        publishToLiveRings(event, bestEffort);
    }

    /**
     * 调用方必须持有 {@code this}。只摘掉环，不在锁内 shutdown。
     */
    private List<RingDispatcher> detachRing(EventRingEnum ring) {
        if (ring == null) {
            return List.of();
        }
        return detachRings(Set.of(ring));
    }

    private List<RingDispatcher> detachRings(Set<EventRingEnum> rings) {
        dispatchersByEventType.clear();
        List<RingDispatcher> retired = new ArrayList<>();
        for (EventRingEnum ring : rings) {
            RingDispatcher removed = globalRingDispatchers.remove(ring);
            if (removed != null) {
                removed.retire();
                retired.add(removed);
            }
        }
        return retired;
    }

    private static void shutdownRetired(List<RingDispatcher> retired) {
        for (RingDispatcher dispatcher : retired) {
            dispatcher.shutdown();
        }
    }

    /**
     * 锁内取仍在册的派发器。某个环已摘掉时只重试该环一次，已成功发出的环不重复发。
     */
    private void publishToLiveRings(MessageEvent event, boolean bestEffort) {
        List<RingDispatcher> targets;
        synchronized (this) {
            targets = new ArrayList<>(loadOrCreateDispatchers(event.getType()));
        }
        if (targets.isEmpty()) {
            return;
        }
        boolean isolate = targets.size() > 1;
        for (RingDispatcher dispatcher : targets) {
            MessageEvent toSend = isolate ? copyEvent(event) : event;
            if (dispatcher.publish(toSend, bestEffort) || !dispatcher.retired) {
                continue;
            }
            RingDispatcher fresh;
            synchronized (this) {
                fresh = globalRingDispatchers.get(dispatcher.ring);
                if (fresh == null) {
                    fresh = getOrCreateRingDispatcher(dispatcher.ring);
                }
            }
            if (fresh != null && fresh != dispatcher) {
                fresh.publish(isolate ? copyEvent(event) : event, bestEffort);
            }
        }
    }

    /** 调用方必须持有 {@code this}。 */
    private Set<RingDispatcher> loadOrCreateDispatchers(EventType eventType) {
        Set<RingDispatcher> cached = dispatchersByEventType.get(eventType);
        if (cached != null) {
            return cached;
        }
        LinkedHashSet<RingDispatcher> set = new LinkedHashSet<>();
        for (EventRingEnum ring : ringsForEventType(eventType)) {
            RingDispatcher dispatcher = getOrCreateRingDispatcher(ring);
            if (dispatcher != null) {
                set.add(dispatcher);
            }
        }
        Set<RingDispatcher> frozen = Set.copyOf(set);
        dispatchersByEventType.put(eventType, frozen);
        return frozen;
    }

    private static MessageEvent copyEvent(MessageEvent event) {
        return new MessageEvent(event.getId(), event.getSource(), event.getType(), event.getPublishTime());
    }

    /** 获取或创建指定环的派发器；若该环当前无任何监听则返回 null。调用方必须持有 this 锁。 */
    private RingDispatcher getOrCreateRingDispatcher(EventRingEnum ring) {
        RingDispatcher d = globalRingDispatchers.get(ring);
        if (d != null) {
            return d;
        }
        List<MessageEventListener<MessageEvent>> ringListeners = listenersOnRingGlobally(ring);
        if (ringListeners.isEmpty()) {
            return null;
        }
        d = buildDispatcher(ring, ringListeners);
        globalRingDispatchers.put(ring, d);
        return d;
    }

    private RingDispatcher buildDispatcher(EventRingEnum ring, List<MessageEventListener<MessageEvent>> ringListeners) {
        ThreadFactory threadFactory = new NamedThreadFactory("event-disruptor-" + ring.name().toLowerCase());
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
        groupBuilder.addSlotClear();
        disruptor.start();
        RingBuffer<DisruptorEvent> ringBuffer = disruptor.getRingBuffer();
        log.info("Disruptor global ring initialized, ring={}, stages={}, listeners={}", ring, byOrder.size(), ringListeners.size());
        return new RingDispatcher(ring, disruptor, ringBuffer, handlerStats);
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

    private final class FilteringListenerHandler implements EventHandler<DisruptorEvent> {

        private final MessageEventListener<MessageEvent> listener;
        private final ListenerExecutionStats stats;
        private final boolean isolateEvent;

        private FilteringListenerHandler(MessageEventListener<MessageEvent> listener, ListenerExecutionStats stats, boolean isolateEvent) {
            this.listener = listener;
            this.stats = stats;
            this.isolateEvent = isolateEvent;
        }

        @Override
        public void onEvent(DisruptorEvent holder, long sequence, boolean endOfBatch) {
            MessageEvent event = holder.event;
            if (event == null || !Objects.equals(listener.type(), event.getType())) {
                return;
            }
            if (isolateEvent) {
                event = copyEvent(event);
            }
            invokeListener(listener, event, stats::record);
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
            boolean isolateEvent = listeners.size() > 1;
            for (int i = 0; i < listeners.size(); i++) {
                MessageEventListener<MessageEvent> l = listeners.get(i);
                ListenerExecutionStats stats = new ListenerExecutionStats(l.getClass().getName(), order);
                handlerStats.add(stats);
                handlers[i] = new FilteringListenerHandler(l, stats, isolateEvent);
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

        /** 全阶段完成后清空槽位引用，避免 RingBuffer 槽长期持有 MessageEvent。 */
        private void addSlotClear() {
            EventHandler<DisruptorEvent> clearer = (holder, sequence, endOfBatch) -> holder.event = null;
            if (current == null) {
                current = disruptor.handleEventsWith(clearer);
            } else {
                current = current.then(clearer);
            }
        }
    }

    private static final class DisruptorEvent {
        private static final EventFactory<DisruptorEvent> EVENT_FACTORY = DisruptorEvent::new;
        private static final EventTranslatorOneArg<DisruptorEvent, MessageEvent> TRANSLATOR =
                (holder, sequence, messageEvent) -> holder.event = messageEvent;
        private MessageEvent event;
    }

    /**
     * 单个物理环的派发器：持有 {@link Disruptor} / {@link RingBuffer}，{@link #publish(MessageEvent)} 即向环内发布。
     */
    private static final class RingDispatcher {
        private static final org.slf4j.Logger RING_LOG =
                org.slf4j.LoggerFactory.getLogger(RingDispatcher.class);
        private final EventRingEnum ring;
        private final Disruptor<DisruptorEvent> disruptor;
        private final RingBuffer<DisruptorEvent> ringBuffer;
        private final List<ListenerExecutionStats> listenerStats;
        private final LongAdder publishedEvents = new LongAdder();
        private final LongAdder droppedEvents = new LongAdder();
        private final java.util.concurrent.atomic.AtomicLong nextDropLogNanos =
                new java.util.concurrent.atomic.AtomicLong();
        private final java.util.concurrent.atomic.AtomicBoolean shutdownOnce =
                new java.util.concurrent.atomic.AtomicBoolean();
        private volatile boolean retired;

        private RingDispatcher(
                EventRingEnum ring,
                Disruptor<DisruptorEvent> disruptor,
                RingBuffer<DisruptorEvent> ringBuffer,
                List<ListenerExecutionStats> listenerStats) {
            this.ring = ring;
            this.disruptor = disruptor;
            this.ringBuffer = ringBuffer;
            this.listenerStats = listenerStats;
        }

        private void retire() {
            retired = true;
        }

        /**
         * @return false 表示环已摘掉或已关闭，调用方可换新环重发；环满丢弃返回 true
         */
        private boolean publish(MessageEvent event, boolean bestEffort) {
            if (retired) {
                return false;
            }
            try {
                if (!bestEffort) {
                    ringBuffer.publishEvent(DisruptorEvent.TRANSLATOR, event);
                    publishedEvents.increment();
                    return true;
                }
                if (ringBuffer.tryPublishEvent(DisruptorEvent.TRANSLATOR, event)) {
                    publishedEvents.increment();
                    return true;
                }
            } catch (RuntimeException ex) {
                RING_LOG.warn("Disruptor ring publish failed, ring={}: {}", ring, ex.toString());
                return false;
            }
            droppedEvents.increment();
            long now = System.nanoTime();
            long next = nextDropLogNanos.get();
            if (now >= next && nextDropLogNanos.compareAndSet(next, now + java.util.concurrent.TimeUnit.SECONDS.toNanos(10))) {
                RING_LOG.warn("Disruptor ring full, event dropped; totalDropped={}", droppedEvents.sum());
            }
            return true;
        }

        private void shutdown() {
            if (!shutdownOnce.compareAndSet(false, true)) {
                return;
            }
            try {
                disruptor.shutdown();
            } catch (RuntimeException ex) {
                RING_LOG.warn("Disruptor ring shutdown failed, ring={}: {}", ring, ex.toString());
            }
        }

        private DisruptorRingMetrics snapshot(EventRingEnum ring) {
            long cursor = ringBuffer.getCursor();
            long minGating = ringBuffer.getMinimumGatingSequence();
            long pending = cursor >= minGating ? cursor - minGating : 0L;
            List<DisruptorListenerExecSnapshot> execSnapshots = new ArrayList<>(listenerStats.size());
            for (ListenerExecutionStats s : listenerStats) {
                execSnapshots.add(s.snapshot());
            }
            return new DisruptorRingMetrics(
                    "*",
                    ring.name(),
                    ringBuffer.getBufferSize(),
                    cursor,
                    minGating,
                    pending,
                    ringBuffer.remainingCapacity(),
                    publishedEvents.sum(),
                    droppedEvents.sum(),
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

    private WaitStrategy createWaitStrategy(EventRingEnum ring) {
        if (ring.getWaitStrategyMode() == EventRingEnum.WaitStrategyMode.YIELDING) {
            return new YieldingWaitStrategy();
        } else if (ring.getWaitStrategyMode() == EventRingEnum.WaitStrategyMode.BUSY_SPIN_WAIT) {
            return new BusySpinWaitStrategy();
        }
        return new BlockingWaitStrategy();
    }





    /**
     * @Author fzx
     * @Description 多播事件
     * @param event
     * @param async 是否异步执行事件 true-异步， false-同步
     */
    @Override
    public void multicastEventWithExecutor(MessageEvent event, boolean async) {
        Executor executor = getTaskExecutor();
        // 遍历所有的事件监听器
        for (MessageEventListener<MessageEvent> listener : getMessageListeners(event)) {
            if (async && executor != null) {
                executor.execute(() -> invokeListener(listener, event));
            } else {
                invokeListener(listener, event);
            }
        }
    }


    /**
     * 对给定的事件执行监听器
     */
    protected void invokeListener(MessageEventListener<MessageEvent> listener, MessageEvent event) {
        try {
            listener.onEvent(event);
        } catch (Throwable err) {
            // 必须传入 throwable，否则 ExceptionInInitializerError 的 getMessage() 常为 null，根因丢失
            log.error("message 监听器 {} 执行事件 {} 失败：{}", listener, event, err.toString(), err);
        }
    }
}
