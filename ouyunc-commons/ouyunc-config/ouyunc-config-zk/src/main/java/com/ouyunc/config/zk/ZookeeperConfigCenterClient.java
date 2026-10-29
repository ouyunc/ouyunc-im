package com.ouyunc.config.zk;

import com.ouyunc.base.config.ConfigConstant;
import com.ouyunc.base.config.ConfigFiles;
import com.ouyunc.base.config.ConfigLoadException;
import com.ouyunc.base.config.ConfigTree;
import com.ouyunc.config.ConfigCenterClient;
import com.ouyunc.config.ConfigCenterType;
import com.ouyunc.config.ConfigLocator;
import com.ouyunc.config.ConfigLogMask;
import com.ouyunc.config.ZkConfigFormat;
import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 从 ZooKeeper 读取配置。
 * <ul>
 *   <li>{@link ZkConfigFormat#YAML}：节点 data 为 YAML 原文；可再读 profile 后缀节点</li>
 *   <li>{@link ZkConfigFormat#PROPERTIES}：父路径下子节点名=key、data=value，展开后 dump 成一份 YAML</li>
 * </ul>
 * 节点不存在视为这一层没有配置，连接失败才抛错。
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
        try (CuratorFramework client = open(locator.zkConnectString())) {
            if (locator.zkFormat() == ZkConfigFormat.PROPERTIES) {
                return loadPropertiesDocuments(client, locator.zkPath());
            }
            return loadYamlDocuments(client, locator, profile);
        }
    }

    /**
     * 整节点 YAML：基础路径 + 可选 profile 路径。
     */
    private static List<String> loadYamlDocuments(CuratorFramework client, ConfigLocator locator, String profile) {
        List<String> documents = new ArrayList<>(2);
        addIfPresent(documents, readPath(client, locator.zkPath()), locator.zkPath());
        String profilePath = ConfigFiles.profileZkPath(locator.zkPath(), profile);
        if (profilePath != null && !profilePath.equals(locator.zkPath())) {
            loadProfile(client, locator, documents, profilePath);
        }
        return documents;
    }

    /**
     * BMS 风格：path 即最终父目录，不再拼 profile 后缀。
     */
    private static List<String> loadPropertiesDocuments(CuratorFramework client, String path) {
        Map<String, String> properties = readChildrenAsProperties(client, path);
        if (properties.isEmpty()) {
            log.info("ZooKeeper properties 父路径不存在或无有效子节点，跳过 path={}", path);
            return List.of();
        }
        Map<String, Object> tree = ConfigTree.fromProperties(properties);
        String yaml = ConfigTree.dumpYaml(tree);
        if (StringUtils.isBlank(yaml)) {
            return List.of();
        }
        log.info("ZooKeeper properties 配置已读取 path={} keys={}\n{}",
                path, properties.size(), ConfigLogMask.maskSensitive(yaml));
        return List.of(yaml);
    }

    /**
     * 列出父路径子节点，节点名作 key、data 作 value。空名或空 data 跳过。
     */
    static Map<String, String> readChildrenAsProperties(CuratorFramework client, String path) {
        Map<String, String> properties = new LinkedHashMap<>();
        List<String> children;
        try {
            children = client.getChildren().forPath(path);
        } catch (KeeperException.NoNodeException ex) {
            return properties;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ConfigLoadException("读取 ZooKeeper 子节点被中断: " + path, ex);
        } catch (Exception ex) {
            throw new ConfigLoadException("读取 ZooKeeper 子节点失败: " + path, ex);
        }
        if (children == null || children.isEmpty()) {
            return properties;
        }
        for (String child : children) {
            if (StringUtils.isBlank(child)) {
                log.info("ZooKeeper 跳过空子节点名 path={}", path);
                continue;
            }
            String childPath = path.endsWith("/") ? path + child : path + "/" + child;
            String value = readPath(client, childPath);
            if (value == null || value.isBlank()) {
                log.info("ZooKeeper 子节点为空，跳过 path={}", childPath);
                continue;
            }
            properties.put(child, value);
        }
        return properties;
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
