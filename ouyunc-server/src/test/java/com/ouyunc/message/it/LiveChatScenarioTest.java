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
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

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
            long now = System.currentTimeMillis();
            LoginContent login = new LoginContent();
            login.setAppKey(APP_KEY);
            login.setIdentity(identity);
            login.setSn("it-" + identity);
            login.setScope(1);
            login.setCreateTime(now);
            login.setSignatureAlgorithm(Encrypt.AsymmetricEncrypt.MD5.getValue());
            login.setSignature(MD5Util.md5(LoginSignatureUtil.buildRaw(APP_KEY, identity, now, APP_SECRET)));
            Message message = new Message();
            message.setId("login-" + identity);
            message.setFrom(identity);
            message.setTo(APP_KEY);
            message.setContentType(MessageContentTypeEnum.LOGIN_REQUEST_CONTENT.getType());
            message.setContent(MessageContents.toJson(login, MessageContentTypeEnum.LOGIN_REQUEST_CONTENT));
            write(MessageTypeEnum.LOGIN.getType(), message);
            Packet ack = awaitType(MessageContentTypeEnum.LOGIN_RESPONSE_SUCCESS_CONTENT.getType(), 15);
            assertNotNull(ack, identity + " 登录无成功回包");
            return ack;
        }

        void sendText(String to, String text) {
            send(MessageTypeEnum.ONE_2_ONE.getType(), to, text, null);
        }

        /** @return 客户端 messageId */
        String send(byte messageType, String to, String text, String correlationId) {
            String id = "msg-" + identity + "-" + System.nanoTime();
            Message message = new Message();
            message.setId(id);
            message.setFrom(identity);
            message.setTo(to);
            message.setContentType(MessageContentTypeEnum.TEXT_CONTENT.getType());
            message.setContent(text);
            message.setCorrelationId(correlationId);
            write(messageType, message);
            return id;
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
