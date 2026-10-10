package com.ouyunc.config.zk;

import com.ouyunc.base.config.ConfigConstant;
import com.ouyunc.base.config.ConfigFiles;
import com.ouyunc.base.config.ConfigLoadException;
import com.ouyunc.config.ConfigCenterClient;
import com.ouyunc.config.ConfigCenterType;
import com.ouyunc.config.ConfigLocator;
import com.ouyunc.config.ConfigLogMask;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 从 ZooKeeper 节点读取 YAML 原文。节点不存在视为这一层没有配置，连接失败才抛错。
 */
public class ZookeeperConfigCenterClient implements ConfigCenterClient {

    private static final Logger log = LoggerFactory.getLogger(ZookeeperConfigCenterClient.class);

    private static final String NIO_SOCKET = "org.apache.zookeeper.ClientCnxnSocketNIO";

    private static final String SASL_CLIENT = "zookeeper.sasl.client";

    static {
        useNioClient();
        disableSaslClientIfUnset();
    }

    @Override
    public ConfigCenterType type() {
        return ConfigCenterType.ZOOKEEPER;
    }

    @Override
    public List<String> loadDocuments(ConfigLocator locator, String profile) {
        useNioClient();
        disableSaslClientIfUnset();
        List<String> documents = new ArrayList<>(2);
        try (CuratorFramework client = open(locator.zkConnectString())) {
            addIfPresent(documents, readPath(client, locator.zkPath()), locator.zkPath());
            String profilePath = ConfigFiles.profileZkPath(locator.zkPath(), profile);
            if (profilePath != null && !profilePath.equals(locator.zkPath())) {
                loadProfile(client, locator, documents, profilePath);
            }
        }
        return documents;
    }

    /**
     * 环境节点连接失败时，基础节点已经读到的内容还在。fail-fast 才整次放弃。
     */
    private static void loadProfile(CuratorFramework client, ConfigLocator locator, List<String> documents, String profilePath) {
        try {
            addIfPresent(documents, readPath(client, profilePath), profilePath);
        } catch (ConfigLoadException ex) {
            if (locator.failFast() || documents.isEmpty()) {
                throw ex;
            }
            log.warn("ZooKeeper 环境配置拉取失败，保留已读到的基础配置 path={}: {}", profilePath, ex.getMessage());
        }
    }

    private static CuratorFramework open(String connectString) {
        log.info("连接 ZooKeeper connectString={}", connectString);
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .sessionTimeoutMs(ConfigConstant.ZK_SESSION_TIMEOUT_MILLIS)
                .connectionTimeoutMs(ConfigConstant.ZK_CONNECT_TIMEOUT_MILLIS)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        client.start();
        try {
            boolean connected = client.blockUntilConnected(ConfigConstant.ZK_CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (!connected) {
                client.close();
                throw new ConfigLoadException("连接 ZooKeeper 超时: " + connectString);
            }
            return client;
        } catch (InterruptedException ex) {
            client.close();
            Thread.currentThread().interrupt();
            throw new ConfigLoadException("连接 ZooKeeper 被中断", ex);
        }
    }

    private static String readPath(CuratorFramework client, String path) {
        try {
            byte[] data = client.getData().forPath(path);
            if (data == null || data.length == 0) {
                return null;
            }
            return new String(data, StandardCharsets.UTF_8);
        } catch (KeeperException.NoNodeException ex) {
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ConfigLoadException("读取 ZooKeeper 配置被中断: " + path, ex);
        } catch (Exception ex) {
            throw new ConfigLoadException("读取 ZooKeeper 配置失败: " + path, ex);
        }
    }

    private static void addIfPresent(List<String> documents, String yaml, String path) {
        if (yaml == null || yaml.isBlank()) {
            log.info("ZooKeeper 节点不存在或为空，跳过 path={}", path);
            return;
        }
        documents.add(yaml);
        log.info("ZooKeeper 配置已读取 path={}\n{}", path, ConfigLogMask.maskSensitive(yaml));
    }

    /**
     * ZooKeeper 3.9 默认用 Netty 客户端。IM 进程已经钉死 Netty 4.2，这里改走 JDK NIO，
     * 必须在 ZooKeeper 类加载之前设置。
     */
    private static void useNioClient() {
        if (System.getProperty("zookeeper.clientCnxnSocket") == null) {
            System.setProperty("zookeeper.clientCnxnSocket", NIO_SOCKET);
        }
    }

    /**
     * 没配 SASL 时关掉客户端认证。ZooKeeper 3.9 连之前会反解服务端主机名，
     * 内网 DNS 无响应时要等十几秒，短超时会在套接字打开前失败。调用方已设置该属性时不覆盖。
     */
    private static void disableSaslClientIfUnset() {
        if (System.getProperty(SASL_CLIENT) == null) {
            System.setProperty(SASL_CLIENT, "false");
        }
    }
}
