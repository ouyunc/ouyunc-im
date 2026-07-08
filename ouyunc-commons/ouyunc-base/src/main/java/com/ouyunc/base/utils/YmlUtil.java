package com.ouyunc.base.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.ttl.TransmittableThreadLocal;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author fzx
 * @Deacription 支持读取${}占位符中的内容
 **/
public class YmlUtil {
    private static final Logger log = LoggerFactory.getLogger(YmlUtil.class);
    private static final String OUYUNC_ENV_SYSTEM_PROPERTY = "ouyunc.env";
    private static final String OUYUNC_ENV_OS_ENV = "OUYUNC_ENV";
    private static final String OUYUNC_ACTIVE_PROFILE_KEY = "ouyunc.profiles.active";
    private static final String SPRING_ACTIVE_PROFILE_KEY = "spring.profiles.active";
    private static final String DEFAULT_PROFILE = "dev";
    private static final ThreadLocal<Boolean> profileLogged = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<String> profileSourceLocal = new TransmittableThreadLocal<>();

    // ${} 占位符 正则表达式
    private static final Pattern p1 = Pattern.compile("\\$\\{.*?\\}");

    private YmlUtil(){
        throw new AssertionError();
    }

    /**
     * key:文件索引名
     * value：配置文件内容
     */
    private static Map<String , LinkedHashMap> ymls = new HashMap<>();
    /**
     * String:当前线程需要查询的文件名
     */
    private static ThreadLocal<String[]> nowFileName = new TransmittableThreadLocal<>();

    private static ThreadLocal<String> profileLocal = new TransmittableThreadLocal<>();

    /**
     * 主动设置，初始化当前线程的环境
     * @param profile
     */
    public static void setProfile(String profile) {
        profileLocal.set(profile);
    }

    /**
     * 加载配置文件
     * @param fileNames
     */
    private static void loadYml(String ...fileNames){
        nowFileName.set(fileNames);
        for (String fileName : fileNames) {
            if (!ymls.containsKey(fileName)){
                InputStream resourceAsStream = YmlUtil.class.getResourceAsStream("/" + fileName);
                if (resourceAsStream != null) {
                    ymls.put(fileName , new Yaml().loadAs(resourceAsStream, LinkedHashMap.class));
                }
            }
        }
    }

    /**
     * 读取yml文件中的某个value。
     * 支持解析 yml文件中的 ${} 占位符
     * @param key
     * @return Object
     */
    private static Object getValueFromYml(String key, String fileName){
        LinkedHashMap valueMap = ymls.get(fileName);
        if (MapUtils.isEmpty(valueMap)) {
            return null;
        }
        if (StringUtils.isBlank(key)) {
            return valueMap;
        }
        String[] keys = key.split("[.]");
        Map ymlInfo = (Map) valueMap.clone();
        for (int i = 0; i < keys.length; i++) {
            Object value = ymlInfo.get(keys[i]);
            if (value == null){
                return null;
            }else if (i < keys.length - 1){
                ymlInfo = (Map) value;
            }else {
                String g;
                String keyChild;
                String v1 = value+"";
                for(Matcher m = p1.matcher(v1); m.find(); value = v1.replace(g, (String)getValueFromYml(keyChild, fileName))) {
                    g = m.group();
                    keyChild = g.replaceAll("\\$\\{", "").replaceAll("\\}", "");
                }
                return value;
            }
        }
        return "";
    }

    /**
     * 读取yml文件中的某个value
     * @param fileNames  yml名称
     * @param key
     * @return Object
     */
    public static Object getValue(String key, String ...fileNames){
        Object value = null;
        if (fileNames == null || fileNames.length == 0) {
            fileNames = new String[]{"bootstrap.yaml", "bootstrap.yml", "application.yaml", "application.yml"};
        }
        loadYml(fileNames);
        // 先从第一个开始找，只要找到就返回，否则就一直遍历，知道找到或遍历完没有值
        for (String fileName : fileNames) {
            // 开始查找某个文件的某个值
            value = getValueFromYml(key, fileName);
            if (value != null && !"".equals(value)) {
                break;
            }
        }
        // 处理驼峰转换？
        return value;
    }

    /**
     * 读取yml文件中的某个value，返回String
     * @param fileName
     * @param key
     * @return String
     */
    public static<T> T getValue(String fileName , String key, Class<T> tClass){
        Object value = getValue(key, fileName);
        return JSON.parseObject(JSON.toJSONString(value), tClass, JSONReader.Feature.SupportClassForName);
    }

    /**
     *  获取 application-test/prod.yml 的配置
     * @param key
     * @return
     */
    public static<T> T getActiveProfileValue(String key, Class<T> tClass){
        String fileName = "application.yml";
        String activeProfiles = getActiveProfiles();
        if (StringUtils.isNotBlank(activeProfiles)) {
            fileName = "application-" + activeProfiles + ".yml";
        }
        return getValue(fileName, key, tClass);
    }

    /**
     *  获取 xxx.yml 的配置
     * @param key
     * @return
     */
    public static<T> T getActiveProfileValue(String fileName, String key,  Class<T> tClass){
        if (StringUtils.isBlank(fileName) || !(fileName.endsWith(".yml") || fileName.endsWith(".yaml"))) {
            throw new RuntimeException("文件名不为空且必须以 .yml 或 .yaml 结尾");
        }
        String activeProfile = getActiveProfiles();
        String activeProfileFileName = buildProfileFileName(fileName, activeProfile);
        logProfileResolutionOnce(activeProfile, fileName, activeProfileFileName);
        if (StringUtils.isNotBlank(activeProfileFileName)) {
            Object value = getValue(key, activeProfileFileName, fileName);
            return JSON.parseObject(JSON.toJSONString(value), tClass, JSONReader.Feature.SupportClassForName);
        }
        return getValue(fileName, key, tClass);
    }



    /**
     * 框架私有方法，非通用。
     * 获取 spring.profiles.active的值: test/prod 测试环境/生成环境
     * @return
     */
    public static String getActiveProfiles(){
        if (profileLocal.get() == null) {
            String value = StringUtils.trimToNull(System.getProperty(OUYUNC_ENV_SYSTEM_PROPERTY));
            String source = "jvm_property:" + OUYUNC_ENV_SYSTEM_PROPERTY;
            if (StringUtils.isBlank(value)) {
                value = StringUtils.trimToNull(System.getenv(OUYUNC_ENV_OS_ENV));
                source = "env_var:" + OUYUNC_ENV_OS_ENV;
            }
            if (StringUtils.isBlank(value)) {
                value = (String) getValue(OUYUNC_ACTIVE_PROFILE_KEY, "ouyunc-server.yml");
                source = "base_config:" + OUYUNC_ACTIVE_PROFILE_KEY;
            }
            if (StringUtils.isBlank(value)) {
                value = (String) getValue(SPRING_ACTIVE_PROFILE_KEY);
                source = "base_config:" + SPRING_ACTIVE_PROFILE_KEY;
            }
            if (StringUtils.isBlank(value)) {
                value = DEFAULT_PROFILE;
                source = "default:" + DEFAULT_PROFILE;
            }
            setProfile(value);
            profileSourceLocal.set(source);
        }
        return profileLocal.get();
    }

    private static String buildProfileFileName(String fileName, String profile) {
        if (StringUtils.isBlank(fileName) || StringUtils.isBlank(profile)) {
            return null;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex >= fileName.length() - 1) {
            return null;
        }
        String baseName = fileName.substring(0, dotIndex);
        String extName = fileName.substring(dotIndex);
        return baseName + "-" + profile + extName;
    }

    private static void logProfileResolutionOnce(String activeProfile, String baseFileName, String profileFileName) {
        if (Boolean.TRUE.equals(profileLogged.get())) {
            return;
        }
        profileLogged.set(true);
        String profileSource = StringUtils.defaultIfBlank(profileSourceLocal.get(), "unknown");
        if (StringUtils.isNotBlank(profileFileName)) {
            log.info("配置加载环境: {}, 来源: {}, 覆盖文件: {}, 基础文件: {}", activeProfile, profileSource, profileFileName, baseFileName);
            return;
        }
        log.info("配置加载环境: {}, 来源: {}, 仅使用基础文件: {}", activeProfile, profileSource, baseFileName);
    }

}
