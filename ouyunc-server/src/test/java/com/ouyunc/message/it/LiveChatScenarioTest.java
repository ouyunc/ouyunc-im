package com.ouyunc.message.it;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.packet.message.content.MessageContents;
import com.ouyunc.base.packet.message.content.MessageSubmissionResponseContent;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.LoginSignatureUtil;
import com.ouyunc.base.utils.MD5Util;
import com.ouyunc.base.utils.PacketReaderWriterUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.sync.RedisCommands;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.yaml.snakeyaml.Yaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 双节点真实私聊：A 登录 6011，B 登录 6012，A 发文本，B 收到。
 * 依赖 docs/sql/it-live-chat.sql 已在 dev 库执行。
 * 运行：-Dim.it=true -Dim.it.host=192.168.1.4
 */
@EnabledIfSystemProperty(named = "im.it", matches = "true")
class LiveChatScenarioTest {

    private static final String APP_KEY = "ouyunc_it";
    private static final String APP_SECRET = "ItSecret#2026";
    private static final String TEXT = "it-hello";
    private static final AtomicLong PACKET_ID = new AtomicLong(9_100_000_000L);

    @Test
    void privateTextCrossesTwoNodes() throws Exception {
        String host = System.getProperty("im.it.host", "192.168.1.4");
        int portA = Integer.parseInt(System.getProperty("im.it.portA", "6011"));
        int portB = Integer.parseInt(System.getProperty("im.it.portB", "6012"));
        NioEventLoopGroup group = new NioEventLoopGroup(2);
        try (ImClient a = new ImClient(group, host, portA, "910001");
             ImClient b = new ImClient(group, host, portB, "910002")) {
            assertEquals(MessageContentTypeEnum.LOGIN_RESPONSE_SUCCESS_CONTENT.getType(), a.login().getMessage().getContentType());
            assertEquals(MessageContentTypeEnum.LOGIN_RESPONSE_SUCCESS_CONTENT.getType(), b.login().getMessage().getContentType());
            Packet received = deliverWithRetry(a, b, MessageTypeEnum.ONE_2_ONE.getType(), "910002", TEXT);
            assertNotNull(received, "重试后 B 仍未收到私聊文本");
            assertEquals(MessageTypeEnum.ONE_2_ONE.getType(), received.getMessageType());
            assertTrue(received.getMessage().getContent().startsWith(TEXT));
        } finally {
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).sync();
        }
    }

    @Test
    void groupTextReachesMemberOnOtherNode() throws Exception {
        String host = System.getProperty("im.it.host", "192.168.1.4");
        NioEventLoopGroup group = new NioEventLoopGroup(2);
        try (ImClient a = new ImClient(group, host, portA(), "910001");
             ImClient b = new ImClient(group, host, portB(), "910002")) {
            a.login();
            b.login();
            String body = "it-group-" + System.nanoTime();
            Packet received = deliverWithRetry(a, b, MessageTypeEnum.GROUP.getType(), "910100", body);
            assertNotNull(received, "重试后 B 仍未收到群文本");
            assertEquals(MessageTypeEnum.GROUP.getType(), received.getMessageType());
            assertTrue(received.getMessage().getContent().startsWith(body));
        } finally {
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).sync();
        }
    }

    @Test
    void alreadyFriendRequestIsAccepted() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try (ImClient a = client(group, portA(), "910001")) {
            a.login();
            MessageSubmissionResponseContent response = submitWithRetry(a,
                    MessageTypeEnum.ONE_2_ONE_FRIEND_REQUEST_JOIN.getType(), "910002", "add-again", null);
            assertEquals("ACCEPTED", response.getStatus());
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        }
    }

    @Test
    void strangerAutoFriendThenPrivateChat() throws Exception {
        String host = System.getProperty("im.it.host", "192.168.1.4");
        NioEventLoopGroup group = new NioEventLoopGroup(2);
        try (ImClient a = new ImClient(group, host, portA(), "910001");
             ImClient stranger = new ImClient(group, host, portB(), "910003")) {
            a.login();
            stranger.login();
            MessageSubmissionResponseContent added = submitWithRetry(a,
                    MessageTypeEnum.ONE_2_ONE_FRIEND_REQUEST_JOIN.getType(), "910003", "auto-add", null);
            assertEquals("ACCEPTED", added.getStatus());
            Packet received = deliverWithRetry(a, stranger, MessageTypeEnum.ONE_2_ONE.getType(), "910003", "it-after-add");
            assertNotNull(received, "自动加好友后私聊未送达");
        } finally {
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).sync();
        }
    }

    @Test
    void joinExistingGroupAcceptedAndImageDelivered() throws Exception {
        String host = System.getProperty("im.it.host", "192.168.1.4");
        NioEventLoopGroup group = new NioEventLoopGroup(2);
        try (ImClient a = new ImClient(group, host, portA(), "910001");
             ImClient b = new ImClient(group, host, portB(), "910002")) {
            a.login();
            b.login();
            MessageSubmissionResponseContent joined = submitWithRetry(a,
                    MessageTypeEnum.GROUP_REQUEST_JOIN.getType(), "910100", "join", null);
            assertEquals("ACCEPTED", joined.getStatus());
            String image = "{\"url\":\"https://cdn/it.png\",\"name\":\"it.png\",\"mime\":\"image/png\",\"size\":3}";
            Packet received = null;
            for (int attempt = 1; attempt <= 3 && received == null; attempt++) {
                String body = image.substring(0, image.length() - 1) + ",\"n\":" + attempt + "}";
                a.sendTyped(MessageTypeEnum.ONE_2_ONE.getType(), "910002", MessageContentTypeEnum.IMAGE_CONTENT.getType(), body, null);
                received = b.awaitText(body, 20);
            }
            assertNotNull(received, "图片消息未送达");
            assertEquals(MessageContentTypeEnum.IMAGE_CONTENT.getType(), received.getMessage().getContentType());
        } finally {
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).sync();
        }
    }

    @Test
    void secondDeviceLoginClosesFirst() throws Exception {
        String host = System.getProperty("im.it.host", "192.168.1.4");
        NioEventLoopGroup group = new NioEventLoopGroup(2);
        try (ImClient first = new ImClient(group, host, portA(), "910001");
             ImClient second = new ImClient(group, host, portB(), "910001")) {
            first.login();
            second.loginWithSn("910001-other");
            boolean kicked = first.awaitType(MessageContentTypeEnum.REMOTE_LOGIN_CONTENT.getType(), 8) != null
                    || first.awaitInactive(8);
            assertTrue(kicked, "旧连接未被异地登录踢下线");
        } finally {
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).sync();
        }
    }

    @Test
    void customerServiceTextDelivered() throws Exception {
        String ticketId = "910200";
        String entry = "cs-entry-it";
        String sessionId = IdentityUtil.sessionId("910001", entry);
        seedCsRoute(ticketId, sessionId, "910001", entry, "910002");
        String host = System.getProperty("im.it.host", "192.168.1.4");
        NioEventLoopGroup group = new NioEventLoopGroup(2);
        try (ImClient visitor = new ImClient(group, host, portA(), "910001");
             ImClient agent = new ImClient(group, host, portB(), "910002")) {
            visitor.loginAs(6, "it-visitor");
            agent.loginAs(5, "it-agent");
            Packet received = null;
            for (int attempt = 1; attempt <= 3 && received == null; attempt++) {
                String body = "it-cs#" + attempt;
                visitor.send(MessageTypeEnum.CUSTOMER_SERVICE.getType(), entry, body, ticketId);
                received = agent.awaitText(body, 20);
            }
            assertNotNull(received, "坐席未收到客服消息");
            assertEquals(MessageTypeEnum.CUSTOMER_SERVICE.getType(), received.getMessageType());
        } finally {
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).sync();
        }
    }

    @SuppressWarnings("unchecked")
    private static void seedCsRoute(String ticketId, String sessionId, String userId, String entry, String assigneeId) throws Exception {
        Path yml = Path.of("src/main/resources/ouyunc-server-dev.yml");
        if (!Files.exists(yml)) {
            yml = Path.of("ouyunc-server/src/main/resources/ouyunc-server-dev.yml");
        }
        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(yml)) {
            root = new Yaml().load(in);
        }
        Map<String, Object> redis = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) root.get("ouyunc")).get("cache")).get("redis");
        RedisURI uri = RedisURI.builder()
                .withHost(String.valueOf(redis.get("host")))
                .withPort(((Number) redis.get("port")).intValue())
                .withPassword(String.valueOf(redis.get("password")).toCharArray())
                .withDatabase(((Number) redis.get("database")).intValue())
                .build();
        RedisClient client = RedisClient.create(uri);
        try (var connection = client.connect()) {
            RedisCommands<String, String> commands = connection.sync();
            String key = CacheConstant.buildCsSessionRouteCacheKey(APP_KEY, ticketId);
            commands.hset(key, Map.of(
                    "ticketId", ticketId,
                    "sessionId", sessionId,
                    "userId", userId,
                    "serviceIdentity", entry,
                    "assigneeId", assigneeId,
                    "status", "1",
                    "channel", "im",
                    "channelType", "im",
                    "agentType", "1",
                    "epoch", "1"));
        } finally {
            client.shutdown();
        }
    }

    @Test
    void heartbeatPongAndHttpPushWithoutKeyAndInviteSelfAndEmptyWithdraw() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try (ImClient a = client(group, portA(), "910001")) {
            a.login();
            a.send(MessageTypeEnum.PING_PONG.getType(), "910001", "ping", null);
            assertNotNull(a.awaitType(MessageContentTypeEnum.PING_PONG_CONTENT.getType(), 8), "心跳无 pong");
            MessageSubmissionResponseContent invite = a.submit(
                    MessageTypeEnum.GROUP_REQUEST_INVITE_JOIN.getType(), "910100",
                    "{\"identity\":\"910001\",\"content\":\"self\"}", null);
            assertEquals("REJECTED", invite.getStatus());
            String withdrawId = a.sendTyped(MessageTypeEnum.ONE_2_ONE.getType(), "910002",
                    MessageContentTypeEnum.WITHDRAW_CONTENT.getType(), "[]", null);
            MessageSubmissionResponseContent withdraw = a.awaitSubmission(withdrawId, 8);
            assertNotNull(withdraw, "撤回无回执");
            assertFalse("ACCEPTED".equals(withdraw.getStatus()));
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        }
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://" + System.getProperty("im.it.host", "192.168.1.4") + ":" + portA() + "/api/im/message/push"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"messageId\":\"x\",\"to\":\"910002\",\"content\":\"hi\"}"))
                .build();
        java.net.http.HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() >= 400, "未带 appKey 的 HTTP 推送应拒绝，实际 " + response.statusCode());
    }

    @Test
    void badSignatureDoesNotLogin() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try (ImClient a = client(group, portA(), "910001")) {
            long now = System.currentTimeMillis();
            LoginContent login = new LoginContent();
            login.setAppKey(APP_KEY);
            login.setIdentity("910001");
            login.setSn("bad");
            login.setScope(1);
            login.setCreateTime(now);
            login.setSignatureAlgorithm(Encrypt.AsymmetricEncrypt.MD5.getValue());
            login.setSignature("not-a-valid-md5");
            Message message = new Message();
            message.setId("login-bad");
            message.setContentType(MessageContentTypeEnum.LOGIN_REQUEST_CONTENT.getType());
            message.setContent(MessageContents.toJson(login, MessageContentTypeEnum.LOGIN_REQUEST_CONTENT));
            a.write(MessageTypeEnum.LOGIN.getType(), message);
            assertTrue(a.awaitType(MessageContentTypeEnum.LOGIN_RESPONSE_SUCCESS_CONTENT.getType(), 3) == null);
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        }
    }

    @Test
    void messageBeforeLoginCloses() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try (ImClient a = client(group, portA(), "910001")) {
            a.sendText("910002", "no-login");
            assertTrue(a.awaitInactive(3));
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        }
    }

    @Test
    void selfFriendRequestAndStrangerChatAndMissingGroupAndCsAreRejected() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try (ImClient a = client(group, portA(), "910001")) {
            a.login();
            assertEquals("REJECTED", a.submit(MessageTypeEnum.ONE_2_ONE_FRIEND_REQUEST_JOIN.getType(), "910001", "add-self", null).getStatus());
            assertEquals("REJECTED", a.submit(MessageTypeEnum.ONE_2_ONE.getType(), "910003", "stranger", null).getStatus());
            assertEquals("REJECTED", a.submit(MessageTypeEnum.GROUP.getType(), "999999", "no-group", null).getStatus());
            String csId = a.send(MessageTypeEnum.CUSTOMER_SERVICE.getType(), "910002", "cs", "missing-ticket");
            MessageSubmissionResponseContent cs = a.awaitSubmission(csId, 8);
            assertNotNull(cs);
            assertFalse("ACCEPTED".equals(cs.getStatus()));
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        }
    }

    /**
     * MQ/Redis 偶发超时时换新正文再发，最多 3 次。每次等待 20 秒。
     */
    private static MessageSubmissionResponseContent submitWithRetry(ImClient from, byte messageType, String target, String text, String correlationId) throws InterruptedException {
        MessageSubmissionResponseContent last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            last = from.submit(messageType, target, text + "#" + attempt, correlationId);
            if ("ACCEPTED".equals(last.getStatus())) {
                return last;
            }
        }
        return last;
    }

    private static Packet deliverWithRetry(ImClient from, ImClient to, byte messageType, String target, String text) throws InterruptedException {
        Packet received = null;
        for (int attempt = 1; attempt <= 3 && received == null; attempt++) {
            String body = text + "#" + attempt;
            from.send(messageType, target, body, null);
            received = to.awaitText(body, 20);
        }
        return received;
    }

    private static ImClient client(NioEventLoopGroup group, int port, String identity) throws InterruptedException {
        return new ImClient(group, System.getProperty("im.it.host", "192.168.1.4"), port, identity);
    }

    private static int portA() {
        return Integer.parseInt(System.getProperty("im.it.portA", "6011"));
    }

    private static int portB() {
        return Integer.parseInt(System.getProperty("im.it.portB", "6012"));
    }

    private static final class ImClient implements AutoCloseable {
        private final Channel channel;
        private final BlockingQueue<Packet> inbox = new LinkedBlockingQueue<>();
        private final String identity;

        ImClient(NioEventLoopGroup group, String host, int port, String identity) throws InterruptedException {
            this.identity = identity;
            this.channel = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(
                                    MessageConstant.MAX_FRAME_LENGTH,
                                    MessageConstant.LENGTH_FIELD_OFFSET,
                                    MessageConstant.LENGTH_FIELD_LENGTH,
                                    MessageConstant.LENGTH_ADJUSTMENT,
                                    MessageConstant.INITIAL_BYTES_TO_STRIP));
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    ByteBuf buf = (ByteBuf) msg;
                                    try {
                                        inbox.offer(PacketReaderWriterUtil.readByteBuf2Packet(buf));
                                    } finally {
                                        buf.release();
                                    }
                                }
                            });
                        }
                    })
                    .connect(host, port)
                    .sync()
                    .channel();
        }

        Packet login() throws InterruptedException {
            return loginAs(1, "it-" + identity);
        }

        Packet loginAs(int scope, String sn) throws InterruptedException {
            long now = System.currentTimeMillis();
            LoginContent login = new LoginContent();
            login.setAppKey(APP_KEY);
            login.setIdentity(identity);
            login.setSn(sn);
            login.setScope(scope);
            login.setCreateTime(now);
            login.setSignatureAlgorithm(Encrypt.AsymmetricEncrypt.MD5.getValue());
            login.setSignature(MD5Util.md5(LoginSignatureUtil.buildRaw(APP_KEY, identity, now, APP_SECRET)));
            Message message = new Message();
            message.setId("login-" + identity + "-" + sn);
            message.setFrom(identity);
            message.setTo(APP_KEY);
            message.setContentType(MessageContentTypeEnum.LOGIN_REQUEST_CONTENT.getType());
            message.setContent(MessageContents.toJson(login, MessageContentTypeEnum.LOGIN_REQUEST_CONTENT));
            write(MessageTypeEnum.LOGIN.getType(), message);
            Packet ack = awaitType(MessageContentTypeEnum.LOGIN_RESPONSE_SUCCESS_CONTENT.getType(), 15);
            assertNotNull(ack, identity + " 登录无成功回包 scope=" + scope);
            return ack;
        }

        void sendText(String to, String text) {
            send(MessageTypeEnum.ONE_2_ONE.getType(), to, text, null);
        }

        /** @return 客户端 messageId */
        String send(byte messageType, String to, String text, String correlationId) {
            return sendTyped(messageType, to, MessageContentTypeEnum.TEXT_CONTENT.getType(), text, correlationId);
        }

        String sendTyped(byte messageType, String to, int contentType, String text, String correlationId) {
            String id = "msg-" + identity + "-" + System.nanoTime();
            Message message = new Message();
            message.setId(id);
            message.setFrom(identity);
            message.setTo(to);
            message.setContentType(contentType);
            message.setContent(text);
            message.setCorrelationId(correlationId);
            write(messageType, message);
            return id;
        }

        Packet loginWithSn(String sn) throws InterruptedException {
            return loginAs(1, sn);
        }

        MessageSubmissionResponseContent submit(byte messageType, String to, String text, String correlationId) throws InterruptedException {
            String id = send(messageType, to, text, correlationId);
            MessageSubmissionResponseContent response = awaitSubmission(id, 8);
            assertNotNull(response, "未收到提交回执 " + id);
            return response;
        }

        MessageSubmissionResponseContent awaitSubmission(String messageId, int seconds) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
            while (System.nanoTime() < deadline) {
                Packet packet = inbox.poll(200, TimeUnit.MILLISECONDS);
                if (packet == null || packet.getMessage() == null) {
                    continue;
                }
                if (packet.getMessage().getContentType() != MessageContentTypeEnum.MESSAGE_SUBMISSION_RESPONSE_CONTENT.getType()) {
                    continue;
                }
                MessageSubmissionResponseContent body = JSON.parseObject(packet.getMessage().getContent(), MessageSubmissionResponseContent.class);
                if (body != null && messageId.equals(body.getMessageId())) {
                    return body;
                }
            }
            return null;
        }

        boolean awaitInactive(int seconds) throws InterruptedException {
            return channel.closeFuture().await(seconds, TimeUnit.SECONDS);
        }

        Packet awaitText(String text, int seconds) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
            while (System.nanoTime() < deadline) {
                Packet packet = inbox.poll(200, TimeUnit.MILLISECONDS);
                if (packet != null && packet.getMessage() != null && text.equals(packet.getMessage().getContent())) {
                    return packet;
                }
            }
            return null;
        }

        private Packet awaitType(int contentType, int seconds) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
            while (System.nanoTime() < deadline) {
                Packet packet = inbox.poll(200, TimeUnit.MILLISECONDS);
                if (packet != null && packet.getMessage() != null && packet.getMessage().getContentType() == contentType) {
                    return packet;
                }
            }
            return null;
        }

        private void write(byte messageType, Message message) {
            Packet packet = new Packet(
                    ProtocolTypeEnum.OUYUNC_CLIENT.getProtocol(),
                    ProtocolTypeEnum.OUYUNC_CLIENT.getProtocolVersion(),
                    PACKET_ID.incrementAndGet(),
                    NumberConstant.NUMBER_1,
                    NumberConstant.NUMBER_2,
                    Encrypt.SymmetryEncrypt.NONE.getValue(),
                    NumberConstant.NUMBER_2,
                    messageType,
                    (byte) 0,
                    message);
            ByteBuf buf = Unpooled.buffer();
            PacketReaderWriterUtil.writePacketInByteBuf(packet, buf);
            channel.writeAndFlush(buf);
        }

        @Override
        public void close() {
            channel.close();
        }
    }
}
