package com.nopadding.internal.handler;

import com.nopadding.internal.SpnegoProxyConstant;
import com.nopadding.internal.kerberos.SpnegoContext;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.security.PrivilegedActionException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import javax.security.auth.login.LoginException;
import lombok.extern.slf4j.Slf4j;
import org.ietf.jgss.GSSException;

/**
 * 处理接入数据的 handler.
 * Client channel 是浏览器链接到 proxy 的 channel，remote channel 是 proxy 到远程主机的 channel.
 */
@Slf4j
public class HttpProxyServerHandler extends ChannelInboundHandlerAdapter {

  private final Properties properties;
  private final Map<String, Channel> remoteChannelMap;
  private volatile Channel remoteChannel;
  private Channel clientChannel;

  private final LinkedList<Object> queue;

  /**
   * 处理接入数据的 handler constructor.
   *
   * @param properties 传入项目参数
   */
  public HttpProxyServerHandler(Properties properties) {
    this.properties = properties;
    remoteChannelMap = new HashMap<>();
    queue = new LinkedList<>();
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    clientChannel = ctx.channel();
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (msg instanceof DefaultHttpRequest) {
      DefaultHttpRequest request = (DefaultHttpRequest) msg;
      String host = request.headers().get("Host");
      if (request.method().equals(HttpMethod.CONNECT)) {
        newSslClient(request, ctx);
      } else {
        if (remoteChannelMap.get(host) == null) {
          newClient(host, request, ctx);
        } else {
          remoteChannel = remoteChannelMap.get(host);
          remoteChannel.write(msg);
        }
      }
    } else if (msg instanceof HttpContent) {
      if (remoteChannel == null) {
        queue.offer(msg);
      } else {
        handleMsg(msg);
      }
    }
  }

  private void handleMsg(Object msg) {
    if (msg instanceof LastHttpContent) {
      remoteChannel.writeAndFlush(msg);
    } else {
      remoteChannel.write(msg);
    }
  }

  private void newSslClient(DefaultHttpRequest request, ChannelHandlerContext ctx) {
    log.info("Connected to {}", request.uri());
    remoteChannel = null;
    String[] addressPair = request.uri().split(":");
    int port = 443;
    if (addressPair.length > 1) {
      port = Integer.parseInt(addressPair[1]);
    }
    String hostName = addressPair[0];
    Bootstrap remoteBootstrap = new Bootstrap().group(clientChannel.eventLoop())
        .channel(NioSocketChannel.class)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
        .option(ChannelOption.SO_KEEPALIVE, true)
        .handler(new ChannelInitializer<SocketChannel>() {
          @Override
          protected void initChannel(SocketChannel ch) {
            ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG));
            // 读取 remote channel，写入 client channel
            ch.pipeline().addLast(new RelayHandler(ctx.channel()));
          }
        });
    remoteBootstrap.connect(hostName, port).addListener((ChannelFutureListener) outboundFuture -> {
      if (outboundFuture.isSuccess()) {
        clientChannel
            .writeAndFlush(
                Unpooled.wrappedBuffer("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes()))
            .addListener((ChannelFutureListener) inboundFuture -> {
              ctx.pipeline().remove(HttpProxyServerHandler.this);
              ctx.pipeline().remove("HttpRequestDecoder");
              // 读取 client channel，写入 remote channel
              ctx.pipeline().addLast(new RelayHandler(outboundFuture.channel()));
            });
      } else {
        clientChannel.write(
            Unpooled.wrappedBuffer("HTTP/1.1 504 Gateway Timeout\r\n\r\n".getBytes()));
        ServerUtils.closeOnFlush(ctx.channel());
      }
      queue.poll();
    });
  }

  private void newClient(String host, DefaultHttpRequest request, ChannelHandlerContext ctx) {
    String[] addressPair = host.split(":");
    final String hostName = addressPair[0];
    final String domain = properties.getProperty(SpnegoProxyConstant.SP_DOMAIN);
    int port = 80;
    if (addressPair.length > 1) {
      port = Integer.parseInt(addressPair[1]);
    }

    if (hostName.endsWith(domain)) {
      try {
        String token = SpnegoContext.getToken(
            properties.getProperty(SpnegoProxyConstant.SP_USERNAME),
            properties.getProperty(SpnegoProxyConstant.SP_PASSWORD),
            hostName
        );
        request.headers().set("Authorization", "Negotiate " + token);
      } catch (GSSException | LoginException | PrivilegedActionException e) {
        log.error("Fail to generate token", e);
      }
    }

    // ignore header Proxy-Connection
    if (request.headers().contains("Proxy-Connection")) {
      request.headers().remove("Proxy-Connection");
    }
    // for http request tools in Java, the uri start with scheme while case error.
    final String uri = request.uri();
    if (uri.startsWith("http://")) {
      request.setUri(getPathFromUri(uri));
    }
    clientChannel.config().setAutoRead(false);
    Bootstrap remoteBootstrap = new Bootstrap()
        .group(clientChannel.eventLoop())
        .channel(NioSocketChannel.class)
        .handler(new ChannelInitializer<SocketChannel>() {
          @Override
          protected void initChannel(SocketChannel ch) {
            ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG));
            ch.pipeline().addLast(new HttpRequestEncoder());
            ch.pipeline().addLast(new HttpProxyRelayHandler(clientChannel));
          }
        });
    remoteBootstrap.connect(hostName, port)
        .addListener((ChannelFutureListener) outboundFuture -> {
          if (outboundFuture.isSuccess()) {
            remoteChannel = outboundFuture.channel();
            remoteChannelMap.put(host, remoteChannel);
            remoteChannel.write(request);
            Object content;
            while ((content = queue.poll()) != null) {
              handleMsg(content);
            }
            clientChannel.config().setAutoRead(true);
          } else {
            clientChannel.write(new DefaultFullHttpResponse(
                request.protocolVersion(), HttpResponseStatus.GATEWAY_TIMEOUT));
            ServerUtils.closeOnFlush(ctx.channel());
          }
        });
  }

  /**
   * remove the scheme://host:port.
   *
   * @param uri uri in http request
   * @return sub string
   */
  private String getPathFromUri(String uri) {
    final String scheme = "http://";
    assert uri.startsWith("http://");
    int i = scheme.length();
    for (; i < uri.length(); i++) {
      if (uri.charAt(i) == '/') {
        break;
      }
    }
    return uri.substring(i);
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) {
    ctx.flush();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.error("Channel exception: ", cause);
    ServerUtils.closeOnFlush(ctx.channel());
  }
}
