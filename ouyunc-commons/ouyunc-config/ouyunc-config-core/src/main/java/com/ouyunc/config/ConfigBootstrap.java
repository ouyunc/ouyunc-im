package com.ouyunc.config;

import com.ouyunc.base.config.ConfigBinder;
import com.ouyunc.base.config.ConfigConstant;
import com.ouyunc.base.config.ConfigFiles;
import com.ouyunc.base.config.ConfigLoadException;
import com.ouyunc.base.config.ConfigRegistry;
import com.ouyunc.base.config.ConfigTree;
import com.ouyunc.base.utils.YmlUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * 启动时把 classpath、外部文件、配置中心合成一份文档，安装到 {@link ConfigRegistry}。
 * 合并安装后会调用 classpath 上的 {@link ConfigBinder}，各模块配置只在这一处绑定。
 */
public final class ConfigBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ConfigBootstrap.class);

    private ConfigBootstrap() {
    }

    /**
     * 使用 classpath 上的 {@link ConfigCenterClient} 完成引导。
     */
    public static void load(String[] args) {
        load(args, ServiceLoader.load(ConfigCenterClient.class));
    }

    /**
     * 测试可传入假的配置中心客户端，避免连真实 Nacos 或 ZooKeeper。
     */
    static void load(String[] args, Iterable<ConfigCenterClient> clients) {
        LocalConfig local = loadLocal(args);
        ConfigLocator locator = ConfigLocator.resolve(local.merged(), local.overrides());
        mergeRemote(local.merged(), locator, local.profile(), clients);
        ConfigTree.resolvePlaceholders(local.merged());
        YmlUtil.setProfile(local.profile());
        ConfigRegistry.install(local.merged(), local.profile());
        log.info("配置合并完成, profile={}, center={}, file={}", local.profile(), locator.centerType(), locator.externalFile());
        bindModules();
    }

    /**
     * 文档安装之后再绑定。各模块只读取 {@link ConfigRegistry}，不在这里建连。
     */
    private static void bindModules() {
        for (ConfigBinder binder : ServiceLoader.load(ConfigBinder.class)) {
            log.info("绑定模块配置: {}", binder.getClass().getName());
            binder.bind();
        }
    }

    /**
     * 环境名先看启动参数，再看外部基础文件，最后才是 classpath。
     * 这样挂载目录里的 {@code ouyunc.profiles.active} 能决定加载哪一份环境文件，以及配置中心的环境 DataId。
     */
    private static LocalConfig loadLocal(String[] args) {
        ConfigOverrides overrides = ConfigOverrides.capture(args);
        Map<String, Object> classpathBase = ConfigSources.readClasspath(ConfigConstant.SERVER_FILE);
        String externalLocation = externalLocation(overrides, classpathBase);
        Map<String, Object> externalBase = ConfigSources.readExternalBase(externalLocation);
        String profile = resolveProfile(classpathBase, externalBase);

        Map<String, Object> merged = new LinkedHashMap<>();
        mergeDocument(merged, classpathBase, "classpath:" + ConfigConstant.SERVER_FILE);
        ConfigSources.mergeClasspath(merged, ConfigFiles.profileFileName(ConfigConstant.SERVER_FILE, profile));
        ConfigSources.mergeExternal(merged, externalLocation, profile, externalBase);
        return new LocalConfig(merged, profile, overrides);
    }

    private static String resolveProfile(Map<String, Object> classpathBase, Map<String, Object> externalBase) {
        String explicit = StringUtils.trimToNull(System.getProperty(ConfigConstant.PROP_ENV));
        if (explicit == null) {
            explicit = StringUtils.trimToNull(System.getenv(ConfigConstant.ENV_ENV));
        }
        if (explicit != null) {
            return explicit;
        }
        String fromExternal = activeProfile(externalBase);
        if (fromExternal != null) {
            return fromExternal;
        }
        String fromClasspath = activeProfile(classpathBase);
        if (fromClasspath != null) {
            return fromClasspath;
        }
        String springProfile = activeText(YmlUtil.getValue(ConfigConstant.SPRING_ACTIVE_PROFILE));
        return springProfile == null ? ConfigConstant.DEFAULT_PROFILE : springProfile;
    }

    private static String externalLocation(ConfigOverrides overrides, Map<String, Object> classpathBase) {
        String location = overrides.get(ConfigConstant.PROP_FILE);
        if (StringUtils.isNotBlank(location)) {
            return location;
        }
        return activeText(ConfigTree.find(classpathBase, ConfigConstant.YAML_FILE));
    }

    private static String activeProfile(Map<String, Object> document) {
        if (document == null) {
            return null;
        }
        return activeText(ConfigTree.find(document, ConfigConstant.YAML_ACTIVE_PROFILE));
    }

    private static String activeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static void mergeDocument(Map<String, Object> target, Map<String, Object> document, String layer) {
        if (document == null) {
            log.info("配置层 [{}] 跳过，文件不存在", layer);
            return;
        }
        ConfigTree.deepMerge(target, document);
        log.info("配置层 [{}] 已合并", layer);
    }

    private static void mergeRemote(Map<String, Object> merged, ConfigLocator locator, String profile,
                                    Iterable<ConfigCenterClient> clients) {
        if (locator.centerType() == ConfigCenterType.NONE) {
            log.info("配置层 [center] 跳过，center.type=none");
            return;
        }
        ConfigCenterClient client = findClient(clients, locator.centerType());
        List<String> documents = loadRemoteDocuments(locator, profile, client);
        if (documents.isEmpty()) {
            log.info("配置层 [{}] 没有可合并的文档", locator.centerType());
            return;
        }
        int index = 0;
        for (String document : documents) {
            if (document == null || document.isBlank()) {
                continue;
            }
            Map<String, Object> parsed = ConfigTree.parseYaml(document);
            ConfigTree.removeLocator(parsed);
            ConfigTree.deepMerge(merged, parsed);
            log.info("配置层 [{}#{}] 已合并", locator.centerType(), index);
            index++;
        }
    }

    private static List<String> loadRemoteDocuments(ConfigLocator locator, String profile, ConfigCenterClient client) {
        try {
            List<String> documents = client.loadDocuments(locator, profile);
            return documents == null ? List.of() : documents;
        } catch (ConfigLoadException ex) {
            if (locator.failFast()) {
                throw ex;
            }
            log.warn("配置中心不可用，继续使用本地配置: {}", ex.getMessage());
            return List.of();
        }
    }

    private static ConfigCenterClient findClient(Iterable<ConfigCenterClient> clients, ConfigCenterType type) {
        if (clients == null) {
            throw missingClient(type);
        }
        Iterator<ConfigCenterClient> iterator = clients.iterator();
        while (iterator.hasNext()) {
            ConfigCenterClient client = iterator.next();
            if (client != null && client.type() == type) {
                return client;
            }
        }
        throw missingClient(type);
    }

    private static IllegalStateException missingClient(ConfigCenterType type) {
        return new IllegalStateException("未找到配置中心实现 " + type
                + "，请确认已引入 ouyunc-config-nacos 或 ouyunc-config-zk");
    }

    private record LocalConfig(Map<String, Object> merged, String profile, ConfigOverrides overrides) {
    }
}
