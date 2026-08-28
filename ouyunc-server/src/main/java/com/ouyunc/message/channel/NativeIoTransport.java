package com.ouyunc.message.channel;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty 4.2 原生 IO 传输选择：Linux epoll → macOS kqueue → JDK NIO。
 * <p>Windows 开发机没有 epoll/kqueue，会自动走 NIO；Linux 生产只要 fat jar 带上
 * {@code netty-transport-native-epoll} 的 classifier，{@link Epoll#isAvailable()} 即为 true。</p>
 */
public final class NativeIoTransport {

    private static final Logger log = LoggerFactory.getLogger(NativeIoTransport.class);

    public enum Kind {
        EPOLL,
        KQUEUE,
        NIO
    }

    private static volatile NativeIoTransport instance;

    private final Kind kind;
    private final Class<? extends ServerSocketChannel> serverChannelClass;
    private final Class<? extends SocketChannel> socketChannelClass;
    private final IoHandlerFactory ioHandlerFactory;

    private NativeIoTransport(Kind kind,
                              Class<? extends ServerSocketChannel> serverChannelClass,
                              Class<? extends SocketChannel> socketChannelClass,
                              IoHandlerFactory ioHandlerFactory) {
        this.kind = kind;
        this.serverChannelClass = serverChannelClass;
        this.socketChannelClass = socketChannelClass;
        this.ioHandlerFactory = ioHandlerFactory;
    }

    /**
     * 服务启动时调用一次；后续 {@link #current()} 复用同一选择，保证服务端与集群客户端通道类型一致。
     */
    public static NativeIoTransport initialize(boolean nativeEnabled) {
        NativeIoTransport detected = detect(nativeEnabled);
        instance = detected;
        log.info("Netty IO 传输: {} (os={}, arch={})",
                detected.kind, System.getProperty("os.name"), System.getProperty("os.arch"));
        if (detected.kind == Kind.NIO && nativeEnabled) {
            log.info("未使用原生传输。epoll: {}；kqueue: {}",
                    causeText(epollUnavailabilityCause()), causeText(kqueueUnavailabilityCause()));
        }
        return detected;
    }

    public static NativeIoTransport current() {
        NativeIoTransport existing = instance;
        if (existing != null) {
            return existing;
        }
        return initialize(true);
    }

    public Kind kind() {
        return kind;
    }

    public Class<? extends ServerSocketChannel> serverChannelClass() {
        return serverChannelClass;
    }

    public Class<? extends SocketChannel> socketChannelClass() {
        return socketChannelClass;
    }

    /**
     * @param nThreads 0 表示使用 Netty 默认（CPU 核数 × 2）
     */
    public EventLoopGroup newGroup(int nThreads, String threadNamePrefix) {
        int threads = Math.max(nThreads, 0);
        return new MultiThreadIoEventLoopGroup(
                threads, new DefaultThreadFactory(threadNamePrefix, true), ioHandlerFactory);
    }

    /**
     * epoll 专属：边沿触发下尽快 ACK；内核 TCP keepalive 作为应用心跳的补充。
     * {@code SO_REUSEPORT} 仅在 boss 线程 &gt; 1 时有意义（多 acceptor 内核负载均衡）。
     */
    public void enhanceServerBootstrap(ServerBootstrap bootstrap, int bossThreads) {
        if (kind != Kind.EPOLL) {
            return;
        }
        if (bossThreads > 1) {
            bootstrap.option(EpollChannelOption.SO_REUSEPORT, Boolean.TRUE);
        }
        bootstrap.childOption(EpollChannelOption.TCP_QUICKACK, Boolean.TRUE);
        bootstrap.childOption(EpollChannelOption.TCP_KEEPIDLE, 60);
        bootstrap.childOption(EpollChannelOption.TCP_KEEPINTVL, 10);
        bootstrap.childOption(EpollChannelOption.TCP_KEEPCNT, 6);
    }

    public void enhanceClientBootstrap(Bootstrap bootstrap) {
        if (kind != Kind.EPOLL) {
            return;
        }
        bootstrap.option(EpollChannelOption.TCP_QUICKACK, Boolean.TRUE);
        bootstrap.option(EpollChannelOption.TCP_KEEPIDLE, 60);
        bootstrap.option(EpollChannelOption.TCP_KEEPINTVL, 10);
        bootstrap.option(EpollChannelOption.TCP_KEEPCNT, 6);
    }

    private static NativeIoTransport detect(boolean nativeEnabled) {
        if (nativeEnabled) {
            try {
                if (Epoll.isAvailable()) {
                    return new NativeIoTransport(Kind.EPOLL, EpollServerSocketChannel.class,
                            EpollSocketChannel.class, EpollIoHandler.newFactory());
                }
            } catch (Throwable t) {
                log.warn("检测 epoll 异常，尝试其他传输: {}", t.toString());
            }
            try {
                if (KQueue.isAvailable()) {
                    return new NativeIoTransport(Kind.KQUEUE, KQueueServerSocketChannel.class,
                            KQueueSocketChannel.class, KQueueIoHandler.newFactory());
                }
            } catch (Throwable t) {
                log.warn("检测 kqueue 异常，回退 NIO: {}", t.toString());
            }
        }
        return new NativeIoTransport(Kind.NIO, NioServerSocketChannel.class,
                NioSocketChannel.class, NioIoHandler.newFactory());
    }

    private static Throwable epollUnavailabilityCause() {
        try {
            return Epoll.unavailabilityCause();
        } catch (Throwable t) {
            return t;
        }
    }

    private static Throwable kqueueUnavailabilityCause() {
        try {
            return KQueue.unavailabilityCause();
        } catch (Throwable t) {
            return t;
        }
    }

    private static String causeText(Throwable cause) {
        return cause == null ? "available-or-unknown" : String.valueOf(cause);
    }
}
