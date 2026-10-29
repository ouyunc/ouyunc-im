package com.ouyunc.base.config;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YAML 配置树的解析、深度合并和占位符展开。
 * <p>
 * Map 按层合并，后者覆盖前者的同名标量。List 整段替换，不做拼接，
 * 否则一份远程白名单会和本地默认值叠在一起，收不回来。
 * </p>
 */
public final class ConfigTree {

    private static final Logger log = LoggerFactory.getLogger(ConfigTree.class);

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^{}]+)}");

    private static final Pattern EXACT_PLACEHOLDER = Pattern.compile("^\\$\\{([^{}]+)}$");

    private ConfigTree() {
    }

    public static Map<String, Object> parseYaml(String yaml) {
        if (StringUtils.isBlank(yaml)) {
            return new LinkedHashMap<>();
        }
        Object loaded;
        try {
            loaded = new Yaml().load(yaml);
        } catch (RuntimeException ex) {
            throw new ConfigLoadException("YAML 解析失败", ex);
        }
        return asRootMap(loaded);
    }

    /**
     * 把配置树写成 YAML 原文，供配置中心「properties 子节点」转成可 merge 的文档。
     */
    public static String dumpYaml(Map<String, Object> root) {
        if (root == null || root.isEmpty()) {
            return "";
        }
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(root);
    }

    /**
     * 把扁平点分 key（如 {@code ouyunc.cache.redis.host}）写入嵌套 Map。
     * 中间段已是标量时用 Map 覆盖并打 warn，与 deepMerge「后者覆盖」一致。
     */
    public static void putDotted(Map<String, Object> root, String dottedKey, Object value) {
        if (root == null || StringUtils.isBlank(dottedKey)) {
            return;
        }
        String[] parts = dottedKey.split("\\.");
        Map<String, Object> current = root;
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (StringUtils.isBlank(part)) {
                continue;
            }
            if (!path.isEmpty()) {
                path.append('.');
            }
            path.append(part);
            Object next = current.get(part);
            if (next instanceof Map<?, ?> map) {
                current = castMap(map);
                continue;
            }
            if (next != null) {
                log.warn("点分配置路径冲突，标量将被 Map 覆盖 path={} existing={}", path, next);
            }
            Map<String, Object> child = new LinkedHashMap<>();
            current.put(part, child);
            current = child;
        }
        String leaf = parts[parts.length - 1];
        if (StringUtils.isBlank(leaf)) {
            return;
        }
        current.put(leaf, value);
    }

    /**
     * 由扁平 key-value 构建嵌套配置树。后写覆盖先写的同名叶子。
     */
    public static Map<String, Object> fromProperties(Map<String, ?> properties) {
        Map<String, Object> root = new LinkedHashMap<>();
        if (properties == null || properties.isEmpty()) {
            return root;
        }
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            if (entry.getKey() == null || StringUtils.isBlank(entry.getKey())) {
                continue;
            }
            putDotted(root, entry.getKey().trim(), entry.getValue());
        }
        return root;
    }

    public static Map<String, Object> parseYaml(InputStream inputStream) {
        if (inputStream == null) {
            return new LinkedHashMap<>();
        }
        Object loaded;
        try {
            loaded = new Yaml().load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (RuntimeException ex) {
            throw new ConfigLoadException("YAML 解析失败", ex);
        }
        return asRootMap(loaded);
    }

    /**
     * 后者覆盖前者。调用方传入的 target 会被改写。
     */
    public static void deepMerge(Map<String, Object> target, Map<?, ?> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey());
            Object sourceValue = entry.getValue();
            Object targetValue = target.get(key);
            if (sourceValue instanceof Map<?, ?> sourceMap && targetValue instanceof Map<?, ?> targetMap) {
                deepMerge(castMap(targetMap), sourceMap);
                continue;
            }
            target.put(key, copyValue(sourceValue));
        }
    }

    public static Map<String, Object> deepCopy(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            copy.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
        }
        return copy;
    }

    /**
     * 去掉远程文档里的 {@code ouyunc.config}，保留本地引导项。
     */
    public static void removeLocator(Map<String, Object> root) {
        if (root == null) {
            return;
        }
        Object ouyunc = root.get(ConfigConstant.ROOT_NODE);
        if (ouyunc instanceof Map<?, ?> raw) {
            castMap(raw).remove(ConfigConstant.CONFIG_NODE);
        }
    }

    /**
     * 按点分路径取值。空白 key 返回整棵树的拷贝，避免调用方改到注册表里的原件。
     */
    public static Object find(Map<String, Object> root, String dottedKey) {
        if (root == null) {
            return null;
        }
        if (StringUtils.isBlank(dottedKey)) {
            return deepCopy(root);
        }
        Object current = root;
        for (String key : dottedKey.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
            if (current == null) {
                return null;
            }
        }
        return copyValue(current);
    }

    /**
     * 在合并完成后展开 ${a.b}。整段都是占位符时保留原类型，嵌在文本里则转成字符串。
     * 找不到的占位符保持原样，避免把可选配置渲成 null。
     */
    public static void resolvePlaceholders(Map<String, Object> root) {
        if (root == null || root.isEmpty()) {
            return;
        }
        resolveNode(root, root, 0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }

    private static Map<String, Object> asRootMap(Object loaded) {
        if (loaded == null) {
            return new LinkedHashMap<>();
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new ConfigLoadException("配置根节点必须是 Map");
        }
        return deepCopy(map);
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return deepCopy(map);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(copyValue(item));
            }
            return copy;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Object resolveNode(Object node, Map<String, Object> root, int depth) {
        if (node == null || depth > ConfigConstant.PLACEHOLDER_MAX_DEPTH) {
            return node;
        }
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> mutable = castMap(map);
            for (String key : new ArrayList<>(mutable.keySet())) {
                mutable.put(key, resolveNode(mutable.get(key), root, depth + 1));
            }
            return mutable;
        }
        if (node instanceof List<?> list) {
            List<Object> mutable = (List<Object>) list;
            for (int i = 0; i < mutable.size(); i++) {
                mutable.set(i, resolveNode(mutable.get(i), root, depth + 1));
            }
            return mutable;
        }
        if (node instanceof String text && text.contains("${")) {
            return resolveText(text, root, depth);
        }
        return node;
    }

    private static Object resolveText(String text, Map<String, Object> root, int depth) {
        Matcher exact = EXACT_PLACEHOLDER.matcher(text);
        if (exact.matches()) {
            Object value = find(root, exact.group(1).trim());
            if (value == null) {
                return text;
            }
            return resolveNode(value, root, depth + 1);
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            Object value = find(root, matcher.group(1).trim());
            String replacement = value == null ? matcher.group() : String.valueOf(value);
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }
}
