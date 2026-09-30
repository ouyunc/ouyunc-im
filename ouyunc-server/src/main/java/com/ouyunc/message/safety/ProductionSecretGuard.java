package com.ouyunc.message.safety;

import com.ouyunc.base.utils.YmlUtil;
import com.ouyunc.message.properties.MessageServerProperties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 生产环境拒绝仓库里的示例签名密钥。
 * <p>
 * 密钥检查只看非空和长度时，提交在 yml 里的示例值会直接生效。
 * dev/test 仍允许示例值，避免本地按仓库配置无法启动；prod/production 直接失败。
 * </p>
 */
public final class ProductionSecretGuard {

    private static final Logger log = LoggerFactory.getLogger(ProductionSecretGuard.class);

    /** 与 ouyunc-server.yml 中的示例值保持一致，改示例时这里必须同步。 */
    static final Set<String> EXAMPLE_SECRETS = Set.of(
            "ouyunc-local-http-push-jwt-secret-32",
            "ouyunc-local-http-admin-jwt-secret-32",
            "dev-ouyunc-cluster-packet-secret-32"
    );

    private ProductionSecretGuard() {
    }

    public static void assertSafe(MessageServerProperties properties) {
        if (properties == null) {
            throw new IllegalStateException("消息服务配置为空，拒绝启动");
        }
        List<String> problems = collect(properties);
        if (problems.isEmpty()) {
            return;
        }
        String profile = YmlUtil.getActiveProfiles();
        String message = "生效配置仍使用示例密钥, profile=" + profile + ", " + String.join("; ", problems);
        if (isProduction(profile)) {
            throw new IllegalStateException(message);
        }
        log.warn("{}. 当前不是生产 profile，仅告警；切到 prod/production 后将拒绝启动", message);
    }

    static List<String> collect(MessageServerProperties properties) {
        List<String> problems = new ArrayList<>();
        if (properties.isHttpPushEnabled() && properties.isHttpPushJwtEnabled()
                && isExample(properties.getHttpPushJwtSecret())) {
            problems.add("ouyunc.message.http-push.jwt.secret");
        }
        if (properties.isHttpAdminEnabled() && isExample(properties.getHttpAdminJwtSecret())) {
            problems.add("ouyunc.message.http-admin.jwt.secret");
        }
        if (properties.isClusterEnable() && isExample(properties.getClusterSecret())) {
            problems.add("ouyunc.message.cluster.secret");
        }
        return problems;
    }

    static boolean isProduction(String profile) {
        if (StringUtils.isBlank(profile)) {
            return false;
        }
        String normalized = profile.trim().toLowerCase(Locale.ROOT);
        return "prod".equals(normalized) || "production".equals(normalized);
    }

    private static boolean isExample(String secret) {
        return StringUtils.isBlank(secret) || EXAMPLE_SECRETS.contains(secret.trim());
    }
}
