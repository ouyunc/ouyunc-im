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
 * 对每个派发器直接调用其 {@code publish}（内部即 {@link RingBuffer#publishEvent}），无额外门面类型。
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
     * 任意环 {@link #invalidateGlobalRing} 时整表清空，避免持有已 shutdown 的实例。
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
        super.addMessageListener(listener);
        if (listener != null && listener.type() != null) {
            dispatchersByEventType.remove(listener.type());
            invalidateGlobalRing(resolveListenerRing(listener));
        }
    }

    @Override
    public void removeMessageListener(MessageEventListener<MessageEvent> listener) {
        EventRingEnum ring = listener != null ? resolveListenerRing(listener) : null;
        EventType type = listener != null ? listener.type() : null;
        super.removeMessageListener(listener);
        if (type != null) {
            dispatchersByEventType.remove(type);
        }
        if (ring != null) {
            invalidateGlobalRing(ring);
        }
    }

    @Override
    public void removeMessageListener(MessageEvent event) {
        EventType eventType = event == null ? null : event.getType();
        Set<EventRingEnum> touched = eventType == null ? Set.of() : ringsForEventType(eventType);
        super.removeMessageListener(event);
        if (eventType != null) {
            dispatchersByEventType.remove(eventType);
            for (EventRingEnum r : touched) {
                invalidateGlobalRing(r);
            }
        }
    }

    @Override
    public void removeAllMessageListeners() {
        synchronized (this) {
            dispatchersByEventType.clear();
            for (RingDispatcher d : globalRingDispatchers.values()) {
                if (d != null) {
                    d.shutdown();
                }
            }
            globalRingDispatchers.clear();
        }
        super.removeAllMessageListeners();
    }

    @Override
    public List<DisruptorRingMetrics> snapshotDisruptorMetrics() {
        if (globalRingDispatchers.isEmpty()) {
            return List.of();
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
        Set<RingDispatcher> targets = dispatchersByEventType.computeIfAbsent(eventType, this::buildDispatchersForEventType);
        if (targets.isEmpty()) {
            return;
        }
        for (RingDispatcher dispatcher : targets) {
            dispatcher.publish(event);
        }
    }

    /**
     * 在同步块内解析类型涉及的各环，并解析/创建对应的 {@link RingDispatcher}（与 {@link #invalidateGlobalRing} 同锁，避免缓存到已失效实例）。
     */
    private Set<RingDispatcher> buildDispatchersForEventType(EventType eventType) {
        synchronized (this) {
            LinkedHashSet<RingDispatcher> set = new LinkedHashSet<>();
            for (EventRingEnum ring : ringsForEventType(eventType)) {
                RingDispatcher d = getOrCreateRingDispatcher(ring);
                if (d != null) {
                    set.add(d);
                }
            }
            return Set.copyOf(set);
        }
    }

    /** 获取或创建指定环的派发器；若该环当前无任何监听则返回 null（不同步，须由调用方持有 this 锁） */
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

    private void invalidateGlobalRing(EventRingEnum ring) {
        synchronized (this) {
            dispatchersByEventType.clear();
            RingDispatcher removed = globalRingDispatchers.remove(ring);
            if (removed != null) {
                removed.shutdown();
            }
        }
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
        disruptor.start();
        RingBuffer<DisruptorEvent> ringBuffer = disruptor.getRingBuffer();
        log.info("Disruptor global ring initialized, ring={}, stages={}, listeners={}", ring, byOrder.size(), ringListeners.size());
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

    private final class FilteringListenerHandler implements EventHandler<DisruptorEvent> {

        private final MessageEventListener<MessageEvent> listener;
        private final ListenerExecutionStats stats;

        private FilteringListenerHandler(MessageEventListener<MessageEvent> listener, ListenerExecutionStats stats) {
            this.listener = listener;
            this.stats = stats;
        }

        @Override
        public void onEvent(DisruptorEvent holder, long sequence, boolean endOfBatch) {
            MessageEvent event = holder.event;
            if (event == null || !Objects.equals(listener.type(), event.getType())) {
                return;
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
            for (int i = 0; i < listeners.size(); i++) {
                MessageEventListener<MessageEvent> l = listeners.get(i);
                ListenerExecutionStats stats = new ListenerExecutionStats(l.getClass().getName(), order);
                handlerStats.add(stats);
                handlers[i] = new FilteringListenerHandler(l, stats);
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

    /**
     * 单个物理环的派发器：持有 {@link Disruptor} / {@link RingBuffer}，{@link #publish(MessageEvent)} 即向环内发布。
     */
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
            // 这里不进行抛出异常，只记录
            log.error("message 监听器 {} 执行事件 {} 失败：{}", listener, event, err.getMessage());
        }
    }
}
