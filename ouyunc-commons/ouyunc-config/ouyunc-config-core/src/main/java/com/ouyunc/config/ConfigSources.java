package com.ouyunc.config;

import com.ouyunc.base.config.ConfigConstant;
import com.ouyunc.base.config.ConfigFiles;
import com.ouyunc.base.config.ConfigLoadException;
import com.ouyunc.base.config.ConfigTree;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * classpath 与外部文件读取。指定了路径却找不到文件时直接失败，
 * 避免运维以为挂载生效了、进程却悄悄用了 jar 里的旧配置。
 */
final class ConfigSources {

    private static final Logger log = LoggerFactory.getLogger(ConfigSources.class);

    private ConfigSources() {
    }

    static Map<String, Object> readClasspath(String fileName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = classLoader == null ? null : classLoader.getResourceAsStream(fileName);
        if (inputStream == null) {
            inputStream = ConfigSources.class.getClassLoader().getResourceAsStream(fileName);
        }
        if (inputStream == null) {
            return null;
        }
        try (InputStream in = inputStream) {
            return ConfigTree.parseYaml(in);
        } catch (IOException ex) {
            throw new ConfigLoadException("读取 classpath 配置失败: " + fileName, ex);
        }
    }

    static boolean mergeClasspath(Map<String, Object> target, String fileName) {
        Map<String, Object> document = readClasspath(fileName);
        if (document == null) {
            log.info("配置层 [classpath:{}] 跳过，文件不存在", fileName);
            return false;
        }
        ConfigTree.deepMerge(target, document);
        log.info("配置层 [classpath:{}] 已合并", fileName);
        return true;
    }

    /**
     * 只读外部基础文件，供决定 profile 使用。目录里没有基础文件时返回 null，不视为失败。
     */
    static Map<String, Object> readExternalBase(String location) {
        if (StringUtils.isBlank(location)) {
            return null;
        }
        Path path = Path.of(location);
        if (!Files.exists(path)) {
            throw new ConfigLoadException("外部配置不存在: " + location);
        }
        if (Files.isDirectory(path)) {
            Path base = path.resolve(ConfigConstant.SERVER_FILE);
            return Files.isRegularFile(base) ? readFile(base) : null;
        }
        return readFile(path);
    }

    static void mergeExternal(Map<String, Object> target, String location, String profile, Map<String, Object> externalBase) {
        if (StringUtils.isBlank(location)) {
            log.info("配置层 [file] 跳过，未指定外部路径");
            return;
        }
        Path path = Path.of(location);
        if (Files.isDirectory(path)) {
            mergeDirectory(target, path, profile, externalBase);
            return;
        }
        ConfigTree.deepMerge(target, externalBase);
        log.info("配置层 [file:{}] 已合并", path.toAbsolutePath());
    }

    private static void mergeDirectory(Map<String, Object> target, Path directory, String profile, Map<String, Object> externalBase) {
        boolean loaded = false;
        if (externalBase != null) {
            ConfigTree.deepMerge(target, externalBase);
            log.info("配置层 [file:{}] 已合并", directory.resolve(ConfigConstant.SERVER_FILE).toAbsolutePath());
            loaded = true;
        }
        String profileFileName = ConfigFiles.profileFileName(ConfigConstant.SERVER_FILE, profile);
        Path profileFile = directory.resolve(profileFileName);
        if (Files.isRegularFile(profileFile)) {
            ConfigTree.deepMerge(target, readFile(profileFile));
            log.info("配置层 [file:{}] 已合并", profileFile.toAbsolutePath());
            loaded = true;
        }
        if (!loaded) {
            throw new ConfigLoadException("外部配置目录中没有 " + ConfigConstant.SERVER_FILE + " 或 " + profileFileName + ": " + directory);
        }
    }

    private static Map<String, Object> readFile(Path path) {
        try {
            return ConfigTree.parseYaml(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new ConfigLoadException("读取外部配置失败: " + path, ex);
        }
    }
}
