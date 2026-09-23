package com.ouyunc.message.cluster.auth;

import io.netty.util.AttributeKey;

/** 集群连接认证常量；只在每条连接首次发送/接收时使用。 */
public final class ClusterAuthConstant {
    public static final String HANDLER_NAME = "clusterAuthentication";
    public static final String AUTH_MESSAGE_ID = "ouyunc-cluster-auth-20261001";
    public static final String HMAC_ALGORITHM = "HmacSHA256";
    public static final int MIN_SECRET_BYTES = 32;
    public static final int MAX_PROOF_LENGTH = 2048;
    /** 固定 Packet 头：序列化字段位于 19，认证包最多 8 KiB。 */
    public static final int SERIALIZER_OFFSET = 19;
    public static final int MAX_AUTH_FRAME_BYTES = 8192;
    public static final long CLOCK_SKEW_MILLIS = 30_000L;
    public static final long AUTH_TIMEOUT_SECONDS = 10L;
    public static final int MAX_RECENT_PROOFS = 100_000;
    public static final AttributeKey<String> TARGET_NODE = AttributeKey.valueOf("clusterAuthTargetNode");
    public static final AttributeKey<String> AUTHENTICATED_NODE = AttributeKey.valueOf("clusterAuthenticatedNode");

    private ClusterAuthConstant() {
    }
}
