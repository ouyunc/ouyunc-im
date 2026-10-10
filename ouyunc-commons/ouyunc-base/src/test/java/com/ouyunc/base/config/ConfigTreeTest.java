package com.ouyunc.base.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ConfigTreeTest {

    @Test
    void deepMergeReplacesListAndKeepsUnmentionedSiblings() {
        Map<String, Object> base = ConfigTree.parseYaml("""
                ouyunc:
                  message:
                    port: 6002
                  proxy:
                    trusted-proxies:
                      - 0.0.0.0/0
                      - ::/0
                """);
        Map<String, Object> overlay = ConfigTree.parseYaml("""
                ouyunc:
                  proxy:
                    trusted-proxies:
                      - 10.0.0.8/32
                """);
        ConfigTree.deepMerge(base, overlay);
        assertEquals(6002, ((Number) ConfigTree.find(base, "ouyunc.message.port")).intValue());
        Object proxies = ConfigTree.find(base, "ouyunc.proxy.trusted-proxies");
        assertInstanceOf(List.class, proxies);
        assertEquals(1, ((List<?>) proxies).size());
        assertEquals("10.0.0.8/32", ((List<?>) proxies).get(0));
    }

    @Test
    void remoteLocatorIsRemovedBeforeMerge() {
        Map<String, Object> local = ConfigTree.parseYaml("""
                ouyunc:
                  config:
                    center:
                      type: nacos
                  message:
                    port: 1
                """);
        Map<String, Object> remote = ConfigTree.parseYaml("""
                ouyunc:
                  config:
                    center:
                      type: zookeeper
                  message:
                    port: 2
                """);
        ConfigTree.removeLocator(remote);
        ConfigTree.deepMerge(local, remote);
        assertEquals("nacos", ConfigTree.find(local, "ouyunc.config.center.type"));
        assertEquals(2, ((Number) ConfigTree.find(local, "ouyunc.message.port")).intValue());
    }

    @Test
    void placeholdersResolveAfterMerge() {
        Map<String, Object> root = ConfigTree.parseYaml("""
                ouyunc:
                  message:
                    port: 6002
                    name: "port-${ouyunc.message.port}"
                    raw: "${missing.key}"
                """);
        ConfigTree.resolvePlaceholders(root);
        assertEquals("port-6002", ConfigTree.find(root, "ouyunc.message.name"));
        assertEquals("${missing.key}", ConfigTree.find(root, "ouyunc.message.raw"));
    }

    @Test
    void profileNamesKeepDirectory() {
        assertEquals("ouyunc-server-dev.yml", ConfigFiles.profileFileName("ouyunc-server.yml", "dev"));
        assertEquals("/ouyunc/im/config/ouyunc-server-pre.yml",
                ConfigFiles.profileZkPath("/ouyunc/im/config/ouyunc-server.yml", "pre"));
    }
}
