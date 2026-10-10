package com.ouyunc.base.config;

/**
 * 配置加载用到的文件名、引导键和环境变量。
 * <p>
 * 引导项（连哪台 Nacos / ZooKeeper、外部文件路径）只从本地文件、进程参数和环境变量解析，
 * 远程文档里的同名节点会被丢掉，避免远端把下一次的连接目标改掉。
 * </p>
 */
public final class ConfigConstant {

    private ConfigConstant() {
    }

    /** 配置中心拉取超时，毫秒 */
    public static final int REMOTE_TIMEOUT_MILLIS = 3000;

    /**
     * ZooKeeper 建立连接的最长等待，毫秒。
     * 未关 SASL 时客户端会先反解主机名，内网 DNS 无响应要十几秒，3 秒会在套接字打开前超时。
     */
    public static final int ZK_CONNECT_TIMEOUT_MILLIS = 15000;

    /** ZooKeeper 会话超时，毫秒 */
    public static final int ZK_SESSION_TIMEOUT_MILLIS = 30000;

    /** 占位符最大展开层数，防止 ${a} -> ${b} -> ${a} 把启动打挂 */
    public static final int PLACEHOLDER_MAX_DEPTH = 8;

    public static final String SERVER_FILE = "ouyunc-server.yml";

    public static final String SERVER_FILE_PREFIX = "ouyunc-server";

    public static final String ROOT_NODE = "ouyunc";

    public static final String CONFIG_NODE = "config";

    public static final String DEFAULT_PROFILE = "dev";

    public static final String DEFAULT_NACOS_GROUP = "OUYUNC_IM";

    public static final String DEFAULT_NACOS_CONTEXT_PATH = "/nacos";

    public static final String DEFAULT_ZK_PATH = "/ouyunc/im/config/ouyunc-server.yml";

    public static final String PROP_ENV = "ouyunc.env";

    public static final String ENV_ENV = "OUYUNC_ENV";

    public static final String PROP_FILE = "ouyunc.config.file";

    public static final String ENV_FILE = "OUYUNC_CONFIG_FILE";

    public static final String PROP_CENTER_TYPE = "ouyunc.config.center.type";

    public static final String ENV_CENTER_TYPE = "OUYUNC_CONFIG_CENTER";

    public static final String PROP_FAIL_FAST = "ouyunc.config.center.fail-fast";

    public static final String ENV_FAIL_FAST = "OUYUNC_CONFIG_FAIL_FAST";

    public static final String PROP_NACOS_ADDR = "ouyunc.config.nacos.server-addr";

    public static final String ENV_NACOS_ADDR = "OUYUNC_NACOS_ADDR";

    public static final String PROP_NACOS_NAMESPACE = "ouyunc.config.nacos.namespace";

    public static final String ENV_NACOS_NAMESPACE = "OUYUNC_NACOS_NAMESPACE";

    public static final String PROP_NACOS_GROUP = "ouyunc.config.nacos.group";

    public static final String ENV_NACOS_GROUP = "OUYUNC_NACOS_GROUP";

    public static final String PROP_NACOS_DATA_ID = "ouyunc.config.nacos.data-id";

    public static final String ENV_NACOS_DATA_ID = "OUYUNC_NACOS_DATA_ID";

    public static final String PROP_NACOS_USERNAME = "ouyunc.config.nacos.username";

    public static final String ENV_NACOS_USERNAME = "OUYUNC_NACOS_USERNAME";

    public static final String PROP_NACOS_PASSWORD = "ouyunc.config.nacos.password";

    public static final String ENV_NACOS_PASSWORD = "OUYUNC_NACOS_PASSWORD";

    public static final String PROP_NACOS_CONTEXT_PATH = "ouyunc.config.nacos.context-path";

    public static final String ENV_NACOS_CONTEXT_PATH = "OUYUNC_NACOS_CONTEXT_PATH";

    public static final String PROP_ZK_CONNECT = "ouyunc.config.zk.connect-string";

    public static final String ENV_ZK_CONNECT = "OUYUNC_ZK_CONNECT";

    public static final String PROP_ZK_PATH = "ouyunc.config.zk.path";

    public static final String ENV_ZK_PATH = "OUYUNC_ZK_PATH";

    /** YAML 路径，和进程参数键一致，方便同一套查找 */
    public static final String YAML_ACTIVE_PROFILE = "ouyunc.profiles.active";

    public static final String SPRING_ACTIVE_PROFILE = "spring.profiles.active";

    public static final String YAML_FILE = "ouyunc.config.file";

    public static final String YAML_CENTER_TYPE = "ouyunc.config.center.type";

    public static final String YAML_FAIL_FAST = "ouyunc.config.center.fail-fast";

    public static final String YAML_NACOS_ADDR = "ouyunc.config.nacos.server-addr";

    public static final String YAML_NACOS_NAMESPACE = "ouyunc.config.nacos.namespace";

    public static final String YAML_NACOS_GROUP = "ouyunc.config.nacos.group";

    public static final String YAML_NACOS_DATA_ID = "ouyunc.config.nacos.data-id";

    public static final String YAML_NACOS_USERNAME = "ouyunc.config.nacos.username";

    public static final String YAML_NACOS_PASSWORD = "ouyunc.config.nacos.password";

    public static final String YAML_NACOS_CONTEXT_PATH = "ouyunc.config.nacos.context-path";

    public static final String YAML_ZK_CONNECT = "ouyunc.config.zk.connect-string";

    public static final String YAML_ZK_PATH = "ouyunc.config.zk.path";
}
