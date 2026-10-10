package com.ouyunc.base.config;

import org.apache.commons.lang3.StringUtils;

/**
 * 服务端配置文件名和 ZooKeeper 路径的环境后缀。
 */
public final class ConfigFiles {

    private ConfigFiles() {
    }

    /**
     * {@code ouyunc-server.yml} 以及 {@code ouyunc-server-{profile}.yml} 都算服务端配置。
     * 安装合并结果之后，这两类名字都读内存文档，不再回退到 classpath 上的旧文件。
     */
    public static boolean isServerConfigFile(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return false;
        }
        if (ConfigConstant.SERVER_FILE.equals(fileName) || "ouyunc-server.yaml".equals(fileName)) {
            return true;
        }
        return fileName.startsWith(ConfigConstant.SERVER_FILE_PREFIX + "-")
                && (fileName.endsWith(".yml") || fileName.endsWith(".yaml"));
    }

    public static boolean allServerConfigFiles(String[] fileNames) {
        if (fileNames == null || fileNames.length == 0) {
            return false;
        }
        for (String fileName : fileNames) {
            if (!isServerConfigFile(fileName)) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code ouyunc-server.yml + dev} 得到 {@code ouyunc-server-dev.yml}。
     */
    public static String profileFileName(String fileName, String profile) {
        if (StringUtils.isBlank(fileName) || StringUtils.isBlank(profile)) {
            return null;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex >= fileName.length() - 1) {
            return fileName + "-" + profile;
        }
        return fileName.substring(0, dotIndex) + "-" + profile + fileName.substring(dotIndex);
    }

    /**
     * {@code /ouyunc/im/config/ouyunc-server.yml + dev}
     * 得到 {@code /ouyunc/im/config/ouyunc-server-dev.yml}。
     */
    public static String profileZkPath(String path, String profile) {
        if (StringUtils.isBlank(path) || StringUtils.isBlank(profile)) {
            return null;
        }
        int slash = path.lastIndexOf('/');
        String directory = slash >= 0 ? path.substring(0, slash + 1) : "";
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return directory + profileFileName(name, profile);
    }
}
