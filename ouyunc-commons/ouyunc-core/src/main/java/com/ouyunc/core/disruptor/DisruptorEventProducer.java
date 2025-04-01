package com.ouyunc.core.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.ouyunc.core.listener.event.GenericEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * disruptor事件生产者
 * @param <T> 数据类型
 */
public class DisruptorEventProducer<T> {
    private final static Logger log = LoggerFactory.getLogger(DisruptorEventProducer.class);

    private final RingBuffer<GenericEvent<T>> ringBuffer;

    public DisruptorEventProducer(RingBuffer<GenericEvent<T>> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    /**
     * 发布事件（带异常处理）
     */
    public void publish(T data) {
        long sequence = ringBuffer.next();
        try {
            GenericEvent<T> event = ringBuffer.get(sequence);
            event.setSource(data);
        } catch (Exception e) {
            // 自定义异常处理
            log.error("数据: {} 发布失败, 原因： {}", data, e.getMessage());
            throw new RuntimeException("Publish failed", e);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    /**
     * 批量发布（高效方式）
     */
    public void publishBatch(List<T> dataList) {
        int batchSize = dataList.size();
        long hi = ringBuffer.next(batchSize);
        long lo = hi - (batchSize - 1);
        try {
            for (long seq = lo; seq <= hi; seq++) {
                GenericEvent<T> event = ringBuffer.get(seq);
                event.setSource(dataList.get((int) (seq - lo)));
            }
        } catch (Exception e) {
            log.error("数据: {} 批量发布失败, 原因： {}", dataList, e.getMessage());
            throw new RuntimeException("Publish failed", e);
        } finally {
            ringBuffer.publish(lo, hi);
        }
    }
}