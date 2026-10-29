package com.ouyunc.message.banner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * @Author fzx
 * @Description: 启动 Banner 打印：优先 classpath:/im-banner.txt，版本优先取构建过滤后的 im-build.properties#revision
 **/
public final class MessageBanner {

    /** classpath 下可自定义的 Banner 文本资源 */
    private static final String BANNER_RESOURCE = "im-banner.txt";

    /**
     * 构建信息：由 Maven resource filtering 将 ${revision} 替换为根 pom 中的真实版本。
     * 仅对该文件开启 filtering，避免误替换 yml 中的占位符。
     */
    private static final String BUILD_PROPERTIES_RESOURCE = "im-build.properties";

    /**
     * Maven 打包写入的标准坐标属性；artifactId 必须与 ouyunc-server/pom.xml 一致。
     * shade 后通常仍保留该文件，可作为第二兜底来源。
     */
    private static final String MAVEN_POM_PROPERTIES = "META-INF/maven/com.ouyunc/ouyunc-server/pom.properties";

    private static final String APP_NAME = "OUYUNC-IM";
    private static final String FALLBACK_VERSION = "unknown";

    /**
     * 内置兜底 Banner：仅当 im-banner.txt 缺失或为空时使用。
     * 故意做成简短文案，避免与资源文件内容相同导致「看起来像没加载资源」。
     */
    private static final String DEFAULT_BANNER =
            """
                      ___  _   ___   ___   _ _   _  ____     ___ __  __\s
                     / _ \\| | | \\ \\ / / | | | \\ | |/ ___|   |_ _|  \\/  |
                    | | | | | | |\\ V /| | | |  \\| | |   _____| || |\\/| |
                    | |_| | |_| | | | | |_| | |\\  | |__|_____| || |  | |
                     \\___/ \\___/  |_|  \\___/|_| \\_|\\____|   |___|_|  |_|
             """;

    private MessageBanner() {
    }

    /**
     * 打印 Banner 与版本号；版本行按 Banner 最大行宽右对齐。
     *
     * @param printStream 输出流，通常为 System.out；为 null 时直接返回
     */
    public static void printBanner(PrintStream printStream) {
        if (printStream == null) {
            return;
        }
        String banner = loadBanner();
        String versionLabel = APP_NAME + "::" + resolveVersion();
        printStream.println(banner);
        printStream.println(rightAlign(versionLabel, maxLineWidth(banner)));
        printStream.flush();
    }

    /**
     * 按目标宽度右对齐文本；文本不短于宽度时原样返回。
     */
    private static String rightAlign(String text, int width) {
        Objects.requireNonNull(text, "text");
        if (width <= 0 || text.length() >= width) {
            return text;
        }
        return " ".repeat(width - text.length()) + text;
    }

    /**
     * 计算 Banner 各行字符宽度（取最长一行），作为版本号右对齐基准。
     * Banner 为 ASCII 艺术字，按字符长度计算即可。
     */
    private static int maxLineWidth(String banner) {
        int max = 0;
        for (String line : banner.split("\\R", -1)) {
            max = Math.max(max, line.length());
        }
        return max;
    }

    /**
     * 优先加载 classpath 下 im-banner.txt；文件存在且非空则使用，否则回退默认 Banner。
     * 会去掉末尾空白行，保证与版本号紧凑显示。
     */
    private static String loadBanner() {
        String fromResource = readClasspathText(BANNER_RESOURCE);
        if (fromResource == null || fromResource.isBlank()) {
            return DEFAULT_BANNER;
        }
        return trimTrailingBlankLines(fromResource);
    }

    /**
     * 版本解析顺序：
     * 1. im-build.properties 中 revision（Maven 过滤根 pom 的 revision）
     * 2. JAR Manifest Implementation-Version（shade ManifestResourceTransformer 写入）
     * 3. META-INF/maven/.../pom.properties 的 version
     * 4. 兜底 FALLBACK_VERSION
     */
    private static String resolveVersion() {
        String revision = readProperty(BUILD_PROPERTIES_RESOURCE, "revision");
        if (isResolvedVersion(revision)) {
            return revision.trim();
        }
        Package pkg = MessageBanner.class.getPackage();
        if (pkg != null) {
            String implVersion = pkg.getImplementationVersion();
            if (isResolvedVersion(implVersion)) {
                return implVersion.trim();
            }
        }
        String mavenVersion = readProperty(MAVEN_POM_PROPERTIES, "version");
        if (isResolvedVersion(mavenVersion)) {
            return mavenVersion.trim();
        }
        return FALLBACK_VERSION;
    }

    /**
     * 已过滤且非占位符的版本才可用；未执行 resource filtering 时仍是 ${revision} 或 @revision@。
     */
    private static boolean isResolvedVersion(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        return !trimmed.contains("${") && !trimmed.contains("@");
    }

    /**
     * 从 classpath 属性文件读取指定 key；资源不存在或 IO 异常时返回 null。
     */
    private static String readProperty(String resourcePath, String key) {
        try (InputStream inputStream = openClasspathResource(resourcePath)) {
            if (inputStream == null) {
                return null;
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            return properties.getProperty(key);
        } catch (IOException ignored) {
            // 启动 Banner 失败不应阻断服务启动，静默回退下一来源
            return null;
        }
    }

    /**
     * 读取 classpath 文本资源为 UTF-8 字符串；不存在或失败返回 null。
     */
    private static String readClasspathText(String resourcePath) {
        try (InputStream inputStream = openClasspathResource(resourcePath)) {
            if (inputStream == null) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException ignored) {
            return null;
        }
    }

    /**
     * 打开 classpath 资源：优先线程上下文 ClassLoader（IDE/容器常见），再回退到本类 ClassLoader。
     * 路径按根路径解析，例如 im-banner.txt → classpath:/im-banner.txt
     */
    private static InputStream openClasspathResource(String resourcePath) {
        String absolutePath = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
        String relativePath = absolutePath.substring(1);

        ClassLoader contextCl = Thread.currentThread().getContextClassLoader();
        if (contextCl != null) {
            InputStream inputStream = contextCl.getResourceAsStream(relativePath);
            if (inputStream != null) {
                return inputStream;
            }
        }

        InputStream fromClass = MessageBanner.class.getResourceAsStream(absolutePath);
        if (fromClass != null) {
            return fromClass;
        }

        ClassLoader classCl = MessageBanner.class.getClassLoader();
        return classCl != null ? classCl.getResourceAsStream(relativePath) : null;
    }

    /**
     * 去掉文本末尾连续空行，避免 Banner 与版本号之间出现大块空白。
     */
    private static String trimTrailingBlankLines(String text) {
        String[] lines = text.split("\\R", -1);
        int end = lines.length;
        while (end > 0 && lines[end - 1].isBlank()) {
            end--;
        }
        if (end == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < end; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines[i]);
        }
        return builder.toString();
    }
}
