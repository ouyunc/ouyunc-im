package com.ouyunc.config.nacos;

import com.ouyunc.base.config.ConfigFiles;
import com.ouyunc.base.config.ConfigLoadException;
import com.ouyunc.config.ConfigCenterClient;
import com.ouyunc.config.ConfigCenterType;
import com.ouyunc.config.ConfigLocator;
import com.ouyunc.config.ConfigLogMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 Nacos 拉取 YAML。基础 DataId 和环境 DataId 各取一次，环境配置不存在就跳过。
 */
public class NacosConfigCenterClient implements ConfigCenterClient {

    private static final Logger log = LoggerFactory.getLogger(NacosConfigCenterClient.class);

    @Override
    public ConfigCenterType type() {
        return ConfigCenterType.NACOS;
    }

    @Override
    public List<String> loadDocuments(ConfigLocator locator, String profile) {
        NacosOpenApi api = new NacosOpenApi();
        List<String> documents = new ArrayList<>(2);
        String baseDataId = locator.nacosDataId();
        addIfPresent(documents, api.getConfig(locator, baseDataId), baseDataId);
        String profileDataId = ConfigFiles.profileFileName(baseDataId, profile);
        if (profileDataId != null && !profileDataId.equals(baseDataId)) {
            loadProfile(api, locator, documents, profileDataId);
        }
        return documents;
    }

    /**
     * 环境层连接失败时，基础层已经读到的内容还在。fail-fast 才整次放弃。
     */
    private static void loadProfile(NacosOpenApi api, ConfigLocator locator, List<String> documents, String profileDataId) {
        try {
            addIfPresent(documents, api.getConfig(locator, profileDataId), profileDataId);
        } catch (ConfigLoadException ex) {
            if (locator.failFast() || documents.isEmpty()) {
                throw ex;
            }
            log.warn("Nacos 环境配置拉取失败，保留已读到的基础配置 dataId={}: {}", profileDataId, ex.getMessage());
        }
    }

    private static void addIfPresent(List<String> documents, String yaml, String dataId) {
        if (yaml == null || yaml.isBlank()) {
            log.info("Nacos 配置不存在，跳过 dataId={}", dataId);
            return;
        }
        documents.add(yaml);
        log.info("Nacos 配置已读取 dataId={}\n{}", dataId, ConfigLogMask.maskSensitive(yaml));
    }
}
