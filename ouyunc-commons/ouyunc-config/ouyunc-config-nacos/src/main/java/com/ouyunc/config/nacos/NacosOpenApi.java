package com.ouyunc.config.nacos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouyunc.base.config.ConfigConstant;
import com.ouyunc.base.config.ConfigLoadException;
import com.ouyunc.config.ConfigLocator;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Nacos Open API v1。Nacos 2.x 和开启兼容接口的 3.x 都可以用这套地址。
 * 多个 server-addr 用逗号分隔，连接失败才换下一台；配置不存在（404）不换节点。
 */
final class NacosOpenApi {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(ConfigConstant.REMOTE_TIMEOUT_MILLIS))
            .build();

    String getConfig(ConfigLocator locator, String dataId) {
        List<String> bases = splitAddresses(locator.nacosServerAddr());
        ConfigLoadException lastFailure = null;
        for (String base : bases) {
            try {
                return getConfigFrom(base, locator, dataId);
            } catch (ConfigLoadException ex) {
                lastFailure = ex;
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new ConfigLoadException("Nacos 地址为空");
    }

    private String getConfigFrom(String base, ConfigLocator locator, String dataId) {
        String token = loginIfNecessary(base, locator);
        String uri = configUri(base, locator, dataId, token);
        HttpResponse<String> response = send(uri, "GET", null);
        int status = response.statusCode();
        if (status == 404) {
            return null;
        }
        if (status == 200) {
            String body = response.body();
            return body == null || body.isBlank() ? null : body;
        }
        throw new ConfigLoadException("读取 Nacos 配置失败, dataId=" + dataId + ", status=" + status);
    }

    private String loginIfNecessary(String base, ConfigLocator locator) {
        if (StringUtils.isBlank(locator.nacosUsername())) {
            return null;
        }
        String form = "username=" + encode(locator.nacosUsername()) + "&password=" + encode(locator.nacosPassword());
        String uri = base + contextPath(locator.nacosContextPath()) + "/v1/auth/login";
        HttpResponse<String> response = send(uri, "POST", form);
        if (response.statusCode() != 200) {
            throw new ConfigLoadException("Nacos 登录失败, status=" + response.statusCode());
        }
        try {
            JsonNode node = MAPPER.readTree(response.body());
            String token = node.path("accessToken").asText("");
            if (token.isBlank()) {
                throw new ConfigLoadException("Nacos 登录响应里没有 accessToken");
            }
            return token;
        } catch (ConfigLoadException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ConfigLoadException("解析 Nacos 登录响应失败", ex);
        }
    }

    private HttpResponse<String> send(String uri, String method, String form) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
                    .timeout(Duration.ofMillis(ConfigConstant.REMOTE_TIMEOUT_MILLIS));
            if ("POST".equals(method)) {
                builder.header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form == null ? "" : form));
            } else {
                builder.GET();
            }
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new ConfigLoadException("访问 Nacos 失败: " + redact(uri), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ConfigLoadException("访问 Nacos 被中断", ex);
        }
    }

    static String configUri(String base, ConfigLocator locator, String dataId, String token) {
        StringBuilder uri = new StringBuilder(base)
                .append(contextPath(locator.nacosContextPath()))
                .append("/v1/cs/configs?dataId=").append(encode(dataId))
                .append("&group=").append(encode(locator.nacosGroup()));
        if (StringUtils.isNotBlank(locator.nacosNamespace())) {
            uri.append("&tenant=").append(encode(locator.nacosNamespace()));
        }
        if (StringUtils.isNotBlank(token)) {
            uri.append("&accessToken=").append(encode(token));
        }
        return uri.toString();
    }

    static String contextPath(String raw) {
        if (StringUtils.isBlank(raw)) {
            return ConfigConstant.DEFAULT_NACOS_CONTEXT_PATH;
        }
        String path = raw.trim();
        if ("/".equals(path)) {
            return "";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    static List<String> splitAddresses(String serverAddr) {
        List<String> bases = new ArrayList<>();
        if (StringUtils.isBlank(serverAddr)) {
            return bases;
        }
        for (String part : serverAddr.split(",")) {
            if (StringUtils.isBlank(part)) {
                continue;
            }
            bases.add(normalizeBase(part));
        }
        return bases;
    }

    static String normalizeBase(String address) {
        String trimmed = address.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://" + trimmed;
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /**
     * 失败日志里不能留下 accessToken。登录密码走 POST body，不会出现在这个地址里。
     */
    static String redact(String uri) {
        if (uri == null) {
            return null;
        }
        return uri.replaceAll("accessToken=[^&]*", "accessToken=***");
    }
}
