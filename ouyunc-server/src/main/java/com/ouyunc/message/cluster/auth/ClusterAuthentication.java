package com.ouyunc.message.cluster.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ouyunc.base.constant.enums.OuyuncMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.SystemClock;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.codec.PacketCodec;
import com.ouyunc.message.cluster.lease.NodeLeaseKeeper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 共用密钥认证：每条 TCP 连接先发送一个 {@link OuyuncMessageTypeEnum#CLUSTER_AUTH} 签名首包，
 * 通过后沿用原有集群协议。签名绑定发送节点、接收节点、时间和随机 nonce，原始密钥不进入 Packet。
 * 认证不负责加密后续流量；跨不可信网络仍须使用现有 TLS。
 * <p>认证首包与心跳 {@link OuyuncMessageTypeEnum#SYN_ACK} 分离；Packet 序列化固定 PROTO_STUFF。</p>
 */
public final class ClusterAuthentication {
    private static final Logger log = LoggerFactory.getLogger(ClusterAuthentication.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    /**
     * 有效签名才占用防重放缓存；容量满拒绝新认证，不淘汰尚有效的记录。
     * 缓存为进程内状态，重启后不保留；时间窗之外的旧签名始终拒绝。
     */
    private static final Cache<String, Boolean> RECENT_PROOFS = Caffeine.newBuilder()
            .expireAfterWrite(ClusterAuthConstant.CLOCK_SKEW_MILLIS * 2, TimeUnit.MILLISECONDS)
            .build();

    private ClusterAuthentication() {
    }

    /**
     * 未认证时只解码受限大小的 PROTO_STUFF 认证首包，禁止在认证之前使用其它序列化算法。
     * 该 Codec 前必须保留 LengthFieldBasedFrameDecoder，确保收到完整且受限的帧。
     */
    public static final class GuardedCodec extends PacketCodec {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            if (ctx.channel().attr(ClusterAuthConstant.AUTHENTICATED_NODE).get() == null
                    && (in.readableBytes() <= ClusterAuthConstant.SERIALIZER_OFFSET
                    || in.readableBytes() > ClusterAuthConstant.MAX_AUTH_FRAME_BYTES
                    || in.getByte(in.readerIndex() + ClusterAuthConstant.SERIALIZER_OFFSET)
                    != Serializer.PROTO_STUFF.getValue())) {
                in.skipBytes(in.readableBytes());
                ctx.close();
                return;
            }
            super.decode(ctx, in, out);
        }
    }

    /** 显式长度限制，未配置密钥时不得退化为匿名集群通信。 */
    public static boolean hasValidSecret(String secret) {
        return secret != null && !secret.isBlank()
                && secret.getBytes(StandardCharsets.UTF_8).length >= ClusterAuthConstant.MIN_SECRET_BYTES;
    }

    /** 认证 DTO，与业务消息元数据分离，不改变现有 Packet 序列化字段。 */
    public record Proof(String from, String to, long issuedAt, String nonce, String signature) {
    }

    private static byte[] signature(String secret, Proof proof) throws Exception {
        Mac mac = Mac.getInstance(ClusterAuthConstant.HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ClusterAuthConstant.HMAC_ALGORITHM));
        // JSON 数组明确字段边界，避免节点名称中分隔符引起签名歧义。
        return mac.doFinal(JSON.writeValueAsBytes(List.of(
                ClusterAuthConstant.AUTH_MESSAGE_ID, proof.from(), proof.to(), proof.issuedAt(), proof.nonce())));
    }

    private static Packet createAuthPacket(Packet first, String secret, String from, String to) throws Exception {
        if (!hasValidSecret(secret) || from == null || from.isBlank() || to == null || to.isBlank()) {
            throw new IllegalStateException("集群认证密钥或节点标识未配置");
        }
        Proof unsigned = new Proof(from, to, TimeUtil.currentTimeMillis(), UUID.randomUUID().toString(), null);
        Proof proof = new Proof(from, to, unsigned.issuedAt(), unsigned.nonce(),
                HexFormat.of().formatHex(signature(secret, unsigned)));
        // 复用合法头字段，不修改原业务 Packet；认证首包由服务端消费，不参与业务幂等。
        Packet auth = first.clone();
        auth.setProtocol(ProtocolTypeEnum.OUYUNC.getProtocol());
        auth.setProtocolVersion(ProtocolTypeEnum.OUYUNC.getProtocolVersion());
        auth.setEncryptType(Encrypt.SymmetryEncrypt.NONE.getValue());
        // Packet 体与集群心跳一致走 PROTO_STUFF；Proof 规范串仍放 Message.content（HMAC 稳定字段边界）。
        auth.setSerializeAlgorithm(Serializer.PROTO_STUFF.getValue());
        auth.setMessageType(OuyuncMessageTypeEnum.CLUSTER_AUTH.getType());
        auth.setMessage(new Message(ClusterAuthConstant.AUTH_MESSAGE_ID, from, to,
                OuyuncMessageContentTypeEnum.AUTH_CONTENT.getType(), JSON.writeValueAsString(proof),
                proof.issuedAt(), new Metadata()));
        return auth;
    }

    private static String verify(Packet packet, String secret, String localNode) throws Exception {
        Message message = packet.getMessage();
        if (!hasValidSecret(secret) || message == null
                || packet.getMessageType() != OuyuncMessageTypeEnum.CLUSTER_AUTH.getType()
                || packet.getSerializeAlgorithm() != Serializer.PROTO_STUFF.getValue()
                || message.getContentType() != OuyuncMessageContentTypeEnum.AUTH_CONTENT.getType()
                || !ClusterAuthConstant.AUTH_MESSAGE_ID.equals(message.getId())
                || message.getContent() == null || message.getContent().length() > ClusterAuthConstant.MAX_PROOF_LENGTH) {
            return null;
        }
        Proof proof = JSON.readValue(message.getContent(), Proof.class);
        long now = TimeUtil.currentTimeMillis();
        if (proof == null || proof.from() == null || proof.from().isBlank()
                || localNode == null || !localNode.equals(proof.to())
                || proof.nonce() == null || proof.signature() == null
                || proof.issuedAt() < now - ClusterAuthConstant.CLOCK_SKEW_MILLIS
                || proof.issuedAt() > now + ClusterAuthConstant.CLOCK_SKEW_MILLIS) {
            return null;
        }
        UUID.fromString(proof.nonce());
        // 期望签名同时用于验签与防重放键，避免同一字节序列因大小写变体绕过 putIfAbsent。
        byte[] expectedSignature = signature(secret, proof);
        byte[] receivedSignature = HexFormat.of().parseHex(proof.signature());
        if (!MessageDigest.isEqual(expectedSignature, receivedSignature)) {
            return null;
        }
        String replayKey = HexFormat.of().formatHex(expectedSignature);
        // 所有 Channel 共用防重放表；串行保护容量检查和插入，仅认证阶段进入此临界区。
        synchronized (RECENT_PROOFS) {
            RECENT_PROOFS.cleanUp();
            if (RECENT_PROOFS.estimatedSize() >= ClusterAuthConstant.MAX_RECENT_PROOFS
                    || RECENT_PROOFS.asMap().putIfAbsent(replayKey, Boolean.TRUE) != null) {
                return null;
            }
        }
        return proof.from();
    }

    /** 出站每条连接只补一次认证包，后续消息直接透传，不增加逐消息签名成本。 */
    public static final class ClientHandler extends ChannelOutboundHandlerAdapter {
        private final String secret;
        private final String localNode;
        private final String targetNode;
        private boolean sent;

        public ClientHandler(String secret, String localNode, String targetNode) {
            this.secret = secret;
            this.localNode = localNode;
            this.targetNode = targetNode;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            try {
                if (!sent && msg instanceof Packet packet) {
                    Packet auth = createAuthPacket(packet, secret, localNode, targetNode);
                    sent = true;
                    ctx.write(auth).addListener(result -> {
                        if (!result.isSuccess()) {
                            ctx.close();
                        }
                    });
                }
                ctx.write(msg, promise);
            } catch (Exception ex) {
                ReferenceCountUtil.release(msg);
                promise.tryFailure(ex);
                log.warn("集群认证首包发送失败，remote={}", ctx.channel().remoteAddress());
                ctx.close();
            }
        }
    }

    /** 入站首包必须为有效签名；认证完成前，任何业务消息和路由消息均不能进入后续处理器。 */
    public static final class ServerHandler extends SimpleChannelInboundHandler<Packet> {
        private final String secret;
        private final String localNode;
        private ScheduledFuture<?> timeout;
        private boolean rejected;

        public ServerHandler(String secret, String localNode) {
            this.secret = secret;
            this.localNode = localNode;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            timeout = ctx.executor().schedule(() -> { ctx.close(); },
                    ClusterAuthConstant.AUTH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
            if (rejected) {
                return;
            }
            try {
                String node = verify(packet, secret, localNode);
                if (node != null) {
                    if (!NodeLeaseKeeper.acceptPeer(node)) {
                        rejected = true;
                        log.warn("拒绝无租约的 OUYUNC 连接 remote={} from={}", ctx.channel().remoteAddress(), node);
                        ctx.close();
                        return;
                    }
                    ctx.channel().attr(ClusterAuthConstant.AUTHENTICATED_NODE).set(node);
                    // 只消费认证首包，不将它转成普通 SYN 心跳；余下业务包继续原流程。
                    ctx.pipeline().remove(this);
                    return;
                }
            } catch (Exception ex) {
                // 不打印认证报文或异常正文，避免日志泄漏可重放的短期凭证。
                log.debug("集群认证首包格式或签名无效，remote={}", ctx.channel().remoteAddress());
            }
            rejected = true;
            log.warn("拒绝未认证的 OUYUNC 连接，remote={}", ctx.channel().remoteAddress());
            ctx.close();
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            cancelTimeout();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            cancelTimeout();
            super.channelInactive(ctx);
        }

        private void cancelTimeout() {
            if (timeout != null) {
                timeout.cancel(false);
            }
        }
    }
}
