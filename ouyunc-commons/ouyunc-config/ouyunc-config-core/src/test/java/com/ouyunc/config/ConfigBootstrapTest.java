package com.ouyunc.config;

import com.ouyunc.base.config.ConfigLoadException;
import com.ouyunc.base.config.ConfigRegistry;
import com.ouyunc.base.utils.YmlUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigBootstrapTest {

    private String previousEnv;

    private String previousFile;

    private String previousFailFast;

    @BeforeEach
    void setUp() {
        previousEnv = System.getProperty("ouyunc.env");
        previousFile = System.getProperty("ouyunc.config.file");
        previousFailFast = System.getProperty("ouyunc.config.center.fail-fast");
        System.setProperty("ouyunc.env", "dev");
        System.clearProperty("ouyunc.config.file");
        System.clearProperty("ouyunc.config.center.fail-fast");
        ConfigRegistry.reset();
        YmlUtil.clearThreadCache();
        RecordingConfigBinder.CALLS.set(0);
    }

    @AfterEach
    void tearDown() {
        restore("ouyunc.env", previousEnv);
        restore("ouyunc.config.file", previousFile);
        restore("ouyunc.config.center.fail-fast", previousFailFast);
        ConfigRegistry.reset();
        YmlUtil.clearThreadCache();
    }

    @Test
    void externalFileAndCenterOverrideClasspath(@TempDir Path tempDir) throws Exception {
        Path external = tempDir.resolve("ouyunc-server.yml");
        Files.writeString(external, """
                ouyunc:
                  message:
                    port: 7000
                  proxy:
                    trusted-proxies:
                      - 10.0.0.8/32
                """);
        System.setProperty("ouyunc.config.file", external.toString());
        FakeNacos center = new FakeNacos("""
                ouyunc:
                  message:
                    port: 8000
                  config:
                    center:
                      type: zookeeper
                """, null);

        ConfigBootstrap.load(new String[0], List.of(center));

        assertEquals(8000, ((Number) ConfigRegistry.find("ouyunc.message.port")).intValue());
        assertEquals("port-8000", ConfigRegistry.find("ouyunc.message.name"));
        assertEquals(1, ((List<?>) ConfigRegistry.find("ouyunc.proxy.trusted-proxies")).size());
        assertEquals("nacos", ConfigRegistry.find("ouyunc.config.center.type"));
        assertEquals(1, RecordingConfigBinder.CALLS.get());
        assertEquals(8000, ((Number) YmlUtil.getValue("ouyunc.message.port", "ouyunc-server-dev.yml")).intValue());
    }

    @Test
    void centerFailureFallsBackWhenFailFastIsOff(@TempDir Path tempDir) throws Exception {
        Path external = tempDir.resolve("ouyunc-server.yml");
        Files.writeString(external, """
                ouyunc:
                  message:
                    port: 7000
                """);
        System.setProperty("ouyunc.config.file", external.toString());
        FakeNacos center = new FakeNacos(null, new ConfigLoadException("nacos down"));

        ConfigBootstrap.load(new String[0], List.of(center));

        assertEquals(7000, ((Number) ConfigRegistry.find("ouyunc.message.port")).intValue());
    }

    @Test
    void centerFailureAbortsWhenFailFastIsOn() {
        System.setProperty("ouyunc.config.center.fail-fast", "true");
        FakeNacos center = new FakeNacos(null, new ConfigLoadException("nacos down"));

        assertThrows(ConfigLoadException.class, () -> ConfigBootstrap.load(new String[0], List.of(center)));
        assertFalse(ConfigRegistry.isInstalled());
        assertEquals(0, RecordingConfigBinder.CALLS.get());
    }

    @Test
    void externalDirectoryProfileWinsWhenEnvIsUnset(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(System.getenv("OUYUNC_ENV") == null);
        System.clearProperty("ouyunc.env");
        YmlUtil.clearThreadCache();
        writeProfileDir(tempDir);
        System.setProperty("ouyunc.config.file", tempDir.toString());
        FakeNacos center = new FakeNacos(null, null);

        ConfigBootstrap.load(new String[0], List.of(center));

        assertEquals("pre", center.seenProfile);
        assertEquals(6200, ((Number) ConfigRegistry.find("ouyunc.message.port")).intValue());
    }

    @Test
    void explicitEnvIgnoresProfileInsideExternalFile(@TempDir Path tempDir) throws Exception {
        writeProfileDir(tempDir);
        System.setProperty("ouyunc.config.file", tempDir.toString());
        FakeNacos center = new FakeNacos(null, null);

        ConfigBootstrap.load(new String[0], List.of(center));

        assertEquals("dev", center.seenProfile);
        assertEquals(6300, ((Number) ConfigRegistry.find("ouyunc.message.port")).intValue());
    }

    @Test
    void missingExternalFileFails() {
        System.setProperty("ouyunc.config.file", "Z:/ouyunc-missing-config.yml");

        assertThrows(ConfigLoadException.class, () -> ConfigBootstrap.load(new String[0], List.of()));
        assertFalse(ConfigRegistry.isInstalled());
    }

    private static void writeProfileDir(Path directory) throws Exception {
        Files.writeString(directory.resolve("ouyunc-server.yml"), """
                ouyunc:
                  profiles:
                    active: pre
                  message:
                    port: 6100
                """);
        Files.writeString(directory.resolve("ouyunc-server-pre.yml"), """
                ouyunc:
                  message:
                    port: 6200
                """);
        Files.writeString(directory.resolve("ouyunc-server-dev.yml"), """
                ouyunc:
                  message:
                    port: 6300
                """);
    }

    private static void restore(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, previous);
    }

    private static final class FakeNacos implements ConfigCenterClient {

        private final String yaml;
        private final ConfigLoadException failure;
        private String seenProfile;

        private FakeNacos(String yaml, ConfigLoadException failure) {
            this.yaml = yaml;
            this.failure = failure;
        }

        @Override
        public ConfigCenterType type() {
            return ConfigCenterType.NACOS;
        }

        @Override
        public List<String> loadDocuments(ConfigLocator locator, String profile) {
            seenProfile = profile;
            if (failure != null) {
                throw failure;
            }
            return yaml == null ? List.of() : List.of(yaml);
        }
    }
}
