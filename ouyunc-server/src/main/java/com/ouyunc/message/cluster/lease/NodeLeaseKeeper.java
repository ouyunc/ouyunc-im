package com.ouyunc.message.cluster.lease;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.NodeLeasePayload;
import com.ouyunc.base.utils.ImSessionPresence;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.cache.distributed.redis.RedisPipelineSupport;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.LoginSessionDirectory;
import com.ouyunc.message.schedule.ScheduleTimer;
import com.ouyunc.message.schedule.TimerTaskWrapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 节点运行生命周期。单机维护本地进程代次、登录目录和配额；集群模式额外维护 Redis 节点租约。
 * 首次任务异步执行，周期采用 fixed-delay；集群心跳与连接数发布共用互斥边界。
 * 每次 start 创建独立运行实例，所有排队任务捕获该实例，禁止旧任务跨越 stop/start 发布数据。
 */
public final class NodeLeaseKeeper {

    private static final Logger log = LoggerFactory.getLogger(NodeLeaseKeeper.class);

    /** 生命周期锁只由 start/stop 使用，工作线程不得获取，避免停机等待形成锁反转。 */
    private static final Object LIFECYCLE_LOCK = new Object();

    private static volatile LeaseRun current;

    /** 同 JVM 重启时保持 epoch 单调；跨进程所有权另外使用随机 ownerToken。 */
    private static long lastEpoch;

    private NodeLeaseKeeper() {
    }

    /** 仅初始化内存和注册调度，不在 Netty bind 回调中执行任何 Redis I/O。 */
    public static void start() {
        synchronized (LIFECYCLE_LOCK) {
            if (current != null) {
                return;
            }
            long epoch = Math.max(TimeUtil.currentTimeMillis(), lastEpoch + 1L);
            LeaseRun run = new LeaseRun(localNodeId(), epoch,
                    MessageServerContext.serverProperties().isClusterEnable());
            lastEpoch = epoch;
            current = run;
            try {
                String taskId = run.clusterMode ? MessageConstant.IM_NODE_LEASE_TASK_ID
                        : MessageConstant.IM_STANDALONE_SESSION_TASK_ID;
                run.heartbeatTask = ScheduleTimer.scheduleSystemWithFixedDelay(
                        taskId, task -> heartbeatOnce(run), 0L,
                        MessageConstant.IM_NODE_LEASE_REFRESH_SECONDS, TimeUnit.SECONDS);
                log.info("IM 节点运行维护已启动 nodeId={} epoch={} clusterMode={}",
                        run.nodeId, epoch, run.clusterMode);
            } catch (RuntimeException e) {
                // 调度失败必须回滚，下一次显式 start 可以重试，不能留下虚假的 STARTED 状态。
                current = null;
                log.error("启动节点租约调度失败 nodeId={}", run.nodeId, e);
                throw e;
            }
        }
    }

    /**
     * 先撤销运行资格，再等待已进入临界区的工作结束，最后按所有权清理 Redis。
     * 不删除发现 SET 成员：跨槽直接 SREM 可能误删新实例，由注册索引原子到期回收。
     */
    public static void stop() {
        synchronized (LIFECYCLE_LOCK) {
            LeaseRun run = current;
            if (run == null) {
                return;
            }
            current = null;
            run.heartbeatTask.cancel();
            run.publishLock.lock();
            run.quotaMaintenanceLock.lock();
            run.directoryMaintenanceLock.lock();
            try {
                if (run.clusterMode) {
                    NodeLeaseRedisSupport.release(CacheFactory.STRING_REDIS.instance(), run.nodeId, run.payloadJson);
                }
            } catch (Exception e) {
                log.warn("停止节点租约清理失败，等待 TTL 回收 nodeId={}", run.nodeId, e);
            } finally {
                run.directoryMaintenanceLock.unlock();
                run.quotaMaintenanceLock.unlock();
                run.publishLock.unlock();
            }
            log.info("IM 节点租约已停止 nodeId={} epoch={}", run.nodeId, run.epoch);
        }
    }

    /** 登录只能使用显式启动的运行实例；停机后的迟到请求不得隐式重启租约。 */
    public static long currentEpoch() {
        LeaseRun run = current;
        if (run == null) {
            throw new IllegalStateException("节点租约尚未启动或已经停止");
        }
        return run.epoch;
    }

    public static String localNodeId() {
        return MessageServerContext.serverProperties().getLocalServerAddress();
    }

    /** 首次成功及故障恢复均以完整的新鲜快照为准，不能仅凭内存初始化允许新登录。 */
    public static boolean isReady() {
        LeaseRun run = current;
        return run != null && run.directoryMaintenanceFresh()
                && (!run.clusterMode || run.snapshot.isFresh());
    }

    /** 本机已有连接在运行期间仍可本地投递；远端成员必须来自未过期的快照。 */
    public static boolean isLive(String nodeId, long nodeEpoch) {
        LeaseRun run = current;
        if (run == null || nodeEpoch <= 0L || StringUtils.isBlank(nodeId)) {
            return false;
        }
        if (nodeId.equals(run.nodeId)) {
            return nodeEpoch == run.epoch;
        }
        if (!run.clusterMode) {
            return false;
        }
        NodeLeasePayload payload = currentSnapshot().leases().get(nodeId);
        return payload != null && payload.getEpoch() == nodeEpoch;
    }

    public static Map<String, Long> snapshot() {
        return currentSnapshot().epochs();
    }

    public static Map<String, NodeLeasePayload> liveLeases() {
        return currentSnapshot().leases();
    }

    /**
     * 过期时只返回本机运行信息并明确标为非权威；既停止使用历史远端路由，也不据此删除 Redis 路由。
     */
    public static NodeLeaseSnapshot currentSnapshot() {
        LeaseRun run = current;
        if (run == null) {
            return new NodeLeaseSnapshot(Map.of(), 0L, false);
        }
        if (!run.clusterMode) {
            return new NodeLeaseSnapshot(Map.of(run.nodeId, run.payload), System.nanoTime(), true);
        }
        NodeLeaseSnapshot snapshot = run.snapshot;
        return snapshot.isFresh() ? snapshot
                : new NodeLeaseSnapshot(Map.of(run.nodeId, run.payload), 0L, false);
    }

    /** 清理方必须校验自己捕获的同一快照，而不是用后来恢复的状态为旧空快照背书。 */
    public static boolean isCurrentSnapshot(NodeLeaseSnapshot snapshot) {
        LeaseRun run = current;
        if (run == null || snapshot == null || !snapshot.isFresh()) {
            return false;
        }
        if (run.clusterMode) {
            return run.snapshot == snapshot;
        }
        NodeLeasePayload local = snapshot.leases().get(run.nodeId);
        return local != null && local.getEpoch() == run.epoch
                && run.payload.getOwnerToken().equals(local.getOwnerToken());
    }

    public static boolean hasLiveLease(String nodeId) {
        return StringUtils.isNotBlank(nodeId) && currentSnapshot().leases().containsKey(nodeId);
    }

    /** HMAC 验证后的成员检查始终只读内存；历史快照过期后拒绝远端握手。 */
    public static boolean acceptPeer(String nodeId) {
        return StringUtils.isNotBlank(nodeId) && !nodeId.equals(localNodeId()) && hasLiveLease(nodeId);
    }

    /**
     * 只读发现索引和租约，不再按跨槽读取结果删除成员。异常由心跳统一记录并按策略摘流。
     * 不强行并入本机：首次发布是否真的成功也必须经过读取验证。
     */
    public static Map<String, NodeLeasePayload> loadLiveLeases() {
        StringRedisTemplate redis = CacheFactory.STRING_REDIS.instance();
        Set<String> nodeIds = redis.opsForSet().members(CacheConstant.buildImNodeSetCacheKey());
        if (nodeIds == null || nodeIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = nodeIds.stream().filter(StringUtils::isNotBlank).toList();
        List<String> keys = ids.stream().map(CacheConstant::buildImNodeLeaseCacheKey).toList();
        List<String> values = RedisPipelineSupport.getStrings(redis, keys);
        return ImSessionPresence.parseLiveLeases(ids, values);
    }

    /** 一个运行实例最多一个待发布任务；调度失败保留 dirty，周期心跳始终会重新发布最新计数。 */
    public static void scheduleConnPublish() {
        LeaseRun run = current;
        if (run != null && run.clusterMode) {
            scheduleConnPublish(run);
        }
    }

    private static void scheduleConnPublish(LeaseRun run) {
        run.connDirty.set(true);
        if (current != run || !run.connPending.compareAndSet(false, true)) {
            return;
        }
        if (ScheduleTimer.scheduleOnce(() -> submitConnPublish(run),
                MessageConstant.IM_NODE_CONN_PUBLISH_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS) == null) {
            run.connPending.set(false);
        }
    }

    private static void submitConnPublish(LeaseRun run) {
        if (current != run) {
            run.connPending.set(false);
            return;
        }
        try {
            ThreadPoolManager.nodeLeaseExecutor().execute(() -> publishConnections(run));
        } catch (Exception e) {
            run.connPending.set(false);
            log.warn("连接数发布提交失败，等待周期心跳 nodeId={}", run.nodeId, e);
        }
    }

    private static void publishConnections(LeaseRun run) {
        // 不排队等待：心跳正在发布时，下一拍会带上最新计数，避免连接风暴积压锁等待任务。
        if (run.publishLock.hasQueuedThreads() || !run.publishLock.tryLock()) {
            run.connPending.set(false);
            return;
        }
        boolean published = false;
        try {
            if (current != run) {
                return;
            }
            run.connDirty.set(false);
            publish(run);
            published = true;
        } catch (Exception e) {
            run.connDirty.set(true);
            log.warn("连接数发布失败，等待周期心跳 nodeId={}", run.nodeId, e);
        } finally {
            run.publishLock.unlock();
            run.connPending.set(false);
        }
        if (published && run.connDirty.get() && current == run) {
            scheduleConnPublish(run);
        }
    }

    /** fixed-delay 防止心跳重叠；共享锁保证它与快速连接数发布、停机清理之间也不会乱序。 */
    private static void heartbeatOnce(LeaseRun run) {
        if (current != run) {
            return;
        }
        if (!run.clusterMode) {
            scheduleMaintenance(run);
            return;
        }
        // fixed-delay 保证仅一个心跳在途，允许它等待当前计数发布；计数任务让出排队优先级。
        // 若这里也 tryLock 后跳过，高频连接变更可能一直抢占锁，导致心跳与快照饥饿。
        run.publishLock.lock();
        try {
            if (current != run) {
                return;
            }
            refreshSnapshot(run);
        } catch (Exception e) {
            log.error("刷新 IM 节点租约失败，旧快照仅在有效期内可用 nodeId={}", run.nodeId, e);
            onLeaseRedisFailure(run);
            return;
        } finally {
            run.publishLock.unlock();
        }
        // 配额及登录 TTL 维护不占用核心续租锁；异常不污染 Redis 租约失败计数。
        scheduleMaintenance(run);
    }

    private static void refreshSnapshot(LeaseRun run) {
        long observedAt = System.nanoTime();
        run.connDirty.set(false);
        publish(run);
        NodeLeaseSnapshot snapshot = new NodeLeaseSnapshot(loadLiveLeases(), observedAt, true);
        NodeLeasePayload local = snapshot.leases().get(run.nodeId);
        if (!snapshot.isFresh() || local == null || !run.payload.getOwnerToken().equals(local.getOwnerToken())) {
            throw new IllegalStateException("本机租约读取不匹配或本轮读取已超时 nodeId=" + run.nodeId);
        }
        if (current != run) {
            return;
        }
        run.snapshot = snapshot;
        run.redisFailStreak = 0;
        if (MessageServerContext.REDIS_ISOLATION_DRAINING.get()) {
            MessageServerContext.exitRedisIsolationDrain();
        }
        try {
            ClusterMembershipReconciler.reconcile(snapshot.leases());
        } catch (Exception e) {
            log.warn("成员连接池收敛失败，等待下轮心跳 nodeId={}", run.nodeId, e);
        }
    }

    private static void publish(LeaseRun run) {
        StringRedisTemplate redis = CacheFactory.STRING_REDIS.instance();
        NodeLeaseRedisSupport.publish(redis, run.nodeId, run.payloadJson, LocalNodeConnCounter.snapshot());
        NodeLeaseRedisSupport.register(redis, run.nodeId);
    }

    private static void scheduleMaintenance(LeaseRun run) {
        if (current != run) {
            return;
        }
        submitQuotaMaintenance(run);
        submitDirectoryMaintenance(run);
    }

    private static void submitQuotaMaintenance(LeaseRun run) {
        if (!run.quotaMaintenancePending.compareAndSet(false, true)) {
            return;
        }
        try {
            ThreadPoolManager.messageProcessorExecutor().execute(() -> maintainQuota(run));
        } catch (Exception e) {
            run.quotaMaintenancePending.set(false);
            log.warn("提交连接配额维护失败 nodeId={}", run.nodeId, e);
        }
    }

    private static void submitDirectoryMaintenance(LeaseRun run) {
        if (!run.directoryMaintenancePending.compareAndSet(false, true)) {
            return;
        }
        try {
            ThreadPoolManager.messageProcessorExecutor().execute(() -> maintainDirectory(run));
        } catch (Exception e) {
            run.directoryMaintenancePending.set(false);
            log.warn("提交登录目录维护失败 nodeId={}", run.nodeId, e);
        }
    }

    private static void maintainQuota(LeaseRun run) {
        run.quotaMaintenanceLock.lock();
        try {
            if (current == run) {
                AppKeyConnQuotaSupport.syncAfterHeartbeat(currentSnapshot());
            }
        } catch (Exception e) {
            log.warn("连接配额维护失败 nodeId={}", run.nodeId, e);
        } finally {
            run.quotaMaintenanceLock.unlock();
            run.quotaMaintenancePending.set(false);
        }
    }

    private static void maintainDirectory(LeaseRun run) {
        run.directoryMaintenanceLock.lock();
        try {
            if (current == run && LoginSessionDirectory.renewLocalLoginTtls()) {
                run.lastDirectorySuccessNanos = System.nanoTime();
            }
        } catch (Exception e) {
            log.warn("登录目录维护失败 nodeId={}", run.nodeId, e);
        } finally {
            run.directoryMaintenanceLock.unlock();
            run.directoryMaintenancePending.set(false);
        }
    }

    /** 仅核心发布/发现失败累加；计数归属于当前运行实例，重启不继承上次故障。 */
    private static void onLeaseRedisFailure(LeaseRun run) {
        if (current != run) {
            return;
        }
        int threshold = MessageServerContext.serverProperties().getClusterIsolationRedisFailThreshold();
        if (threshold <= 0) {
            threshold = MessageConstant.IM_NODE_LEASE_FAILURE_THRESHOLD;
        }
        if (run.redisFailStreak < threshold) {
            run.redisFailStreak++;
        }
        String action = StringUtils.trimToEmpty(MessageServerContext.serverProperties().getClusterIsolationAction());
        if (MessageConstant.CLUSTER_ISOLATION_ACTION_DRAIN_ON_REDIS_LOSS.equalsIgnoreCase(action)
                && run.redisFailStreak >= threshold && !MessageServerContext.REDIS_ISOLATION_DRAINING.get()) {
            MessageServerContext.enterRedisIsolationDrain();
        }
    }

    /** 所有异步状态按运行实例隔离，旧回调不会清除新实例的 pending/dirty 或覆盖新快照。 */
    private static final class LeaseRun {
        private final String nodeId;
        private final long epoch;
        private final NodeLeasePayload payload;
        private final String payloadJson;
        private final boolean clusterMode;
        private final ReentrantLock publishLock = new ReentrantLock(true);
        private final ReentrantLock quotaMaintenanceLock = new ReentrantLock();
        private final ReentrantLock directoryMaintenanceLock = new ReentrantLock();
        private final AtomicBoolean connPending = new AtomicBoolean();
        private final AtomicBoolean connDirty = new AtomicBoolean();
        private final AtomicBoolean quotaMaintenancePending = new AtomicBoolean();
        private final AtomicBoolean directoryMaintenancePending = new AtomicBoolean();
        private volatile NodeLeaseSnapshot snapshot = new NodeLeaseSnapshot(Map.of(), 0L, false);
        private volatile long lastDirectorySuccessNanos;
        private TimerTaskWrapper heartbeatTask;
        private int redisFailStreak;

        private LeaseRun(String nodeId, long epoch, boolean clusterMode) {
            this.nodeId = nodeId;
            this.epoch = epoch;
            this.clusterMode = clusterMode;
            this.payload = new NodeLeasePayload(epoch,
                    MessageServerContext.serverProperties().getClusterZoneId(), nodeId);
            this.payload.setOwnerToken(UUID.randomUUID().toString());
            this.payloadJson = payload.toJson();
        }

        private boolean directoryMaintenanceFresh() {
            long successAt = lastDirectorySuccessNanos;
            return successAt > 0L && System.nanoTime() - successAt < TimeUnit.SECONDS.toNanos(
                    MessageConstant.IM_LOGIN_DIRECTORY_READY_SECONDS);
        }
    }
}
