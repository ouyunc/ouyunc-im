package com.ouyunc.message.http;

import com.ouyunc.base.constant.MessageConstant;
import io.netty.handler.codec.http.FullHttpRequest;

/**
 * HTTP 分发的节点级准入预算，覆盖所有业务执行器、同步入口及异步业务。
 * 接收阶段先申请额度，聚合完成后交给分发器；只保护计数的短临界区，不在锁内运行业务。
 */
public final class HttpRequestAdmission {
    private static int inFlight;
    private static long retainedBytes;
    private static long rejected;

    private HttpRequestAdmission() { }

    /** 容量按实际 ByteBuf 容量估计；无正文请求也占条数和对象预算。 */
    public static synchronized Lease tryAcquire(FullHttpRequest request) {
        long bytes = (long) request.content().capacity() + MessageConstant.HTTP_REQUEST_OVERHEAD_BYTES;
        return tryAcquire(bytes);
    }

    /** 在聚合前预留请求对象预算，正文随分块到达增量计费。 */
    public static synchronized Lease tryAcquire(long bytes) {
        if (inFlight >= MessageConstant.HTTP_NODE_MAX_IN_FLIGHT
                || bytes > MessageConstant.HTTP_NODE_MAX_RETAINED_BYTES - retainedBytes) {
            rejected++;
            return null;
        }
        inFlight++;
        retainedBytes += bytes;
        return new Lease(bytes, true);
    }

    /** 无正文的内置探活同步执行，不进入业务队列，避免过载引发存活探针误杀。 */
    static Lease probe() { return new Lease(0L, false); }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(inFlight, retainedBytes, rejected, MessageConstant.HTTP_NODE_MAX_IN_FLIGHT,
                MessageConstant.HTTP_NODE_MAX_RETAINED_BYTES);
    }

    public record Snapshot(int inFlight, long retainedBytes, long rejected, int maxInFlight, long maxBytes) { }

    private static synchronized void release(long bytes) {
        inFlight--;
        retainedBytes -= bytes;
        HttpRequestAdmission.class.notifyAll();
    }

    private static synchronized boolean reserveBytes(long bytes) {
        if (bytes < 0 || bytes > MessageConstant.HTTP_NODE_MAX_RETAINED_BYTES - retainedBytes) {
            rejected++;
            return false;
        }
        retainedBytes += bytes;
        return true;
    }

    /** 停机等待在途请求（包括已超时但仍执行的异步业务），使用单调时钟及统一期限。 */
    public static synchronized boolean awaitEmpty(long deadlineNanos) throws InterruptedException {
        while (inFlight > 0) {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0) {
                return false;
            }
            java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(HttpRequestAdmission.class, remaining);
        }
        return true;
    }

    /**
     * 分发/响应持有一份，异步业务另持一份；超时或断连只释放响应份额。
     * 未结束的业务仍占准入额度，防止请求超时后继续入队绕过节点预算。
     */
    public static final class Lease implements AutoCloseable {
        private long bytes;
        private final boolean accounted;
        private boolean responseClosed;
        private boolean businessRunning;
        private boolean released;
        private Runnable resourceCleanup;
        private Lease(long bytes, boolean accounted) { this.bytes = bytes; this.accounted = accounted; }

        /** 仅接收阶段调用，成功后该分块才允许进入聚合器。 */
        public synchronized boolean addBytes(long additionalBytes) {
            if (responseClosed || !accounted || !reserveBytes(additionalBytes)) {
                return false;
            }
            bytes += additionalBytes;
            return true;
        }

        /** 必须先于注册可能立即执行的关闭回调；仅异步业务调用一次。 */
        public synchronized void retainBusiness(Runnable cleanup) {
            if (responseClosed) {
                throw new IllegalStateException("HTTP admission already closed");
            }
            businessRunning = true;
            resourceCleanup = cleanup;
        }
        public synchronized void completeBusiness() {
            businessRunning = false;
            releaseIfFinished();
        }
        @Override
        public synchronized void close() {
            responseClosed = true;
            releaseIfFinished();
        }
        private void releaseIfFinished() {
            if (responseClosed && !businessRunning && !released) {
                released = true;
                try {
                    // 响应写出也可能使用业务返回的上传资源，因此两方结束后才能销毁。
                    if (resourceCleanup != null) {
                        resourceCleanup.run();
                        resourceCleanup = null;
                    }
                } finally {
                    if (accounted) {
                        release(bytes);
                    }
                }
            }
        }
    }
}
