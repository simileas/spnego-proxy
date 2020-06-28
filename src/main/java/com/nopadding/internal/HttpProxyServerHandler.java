package com.nopadding.internal;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.util.Base64;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

@Slf4j
public class HttpProxyServerHandler extends ChannelInboundHandlerAdapter {

  private final Bootstrap relayBootstrap = new Bootstrap();
  private final Properties properties;

  HttpProxyServerHandler(Properties properties) {
    this.properties = properties;
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof FullHttpRequest) {
      FullHttpRequest request = (FullHttpRequest) msg;
      final Channel inboundChannel = ctx.channel();

      String host = request.headers().get("Host");
      String[] addressPair = host.split(":");
      String hostName = addressPair[0];
      int port = 80;
      if (addressPair.length > 1) {
        port = Integer.parseInt(addressPair[1]);
      }

      if (HttpMethod.CONNECT.equals(request.method())) {
        addressPair = request.uri().split(":");
        port = 443;
        if (addressPair.length > 1) {
          port = Integer.parseInt(addressPair[1]);
        }
        hostName = addressPair[0];

        relayBootstrap.group(inboundChannel.eventLoop())
            .channel(NioSocketChannel.class)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .handler(new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG));
                ch.pipeline().addLast(new RelayHandler(ctx.channel()));
              }
            });
        relayBootstrap.connect(hostName, port).addListener(new ChannelFutureListener() {
          @Override
          public void operationComplete(ChannelFuture outboundFuture) {
            if (outboundFuture.isSuccess()) {
              inboundChannel
                  .writeAndFlush(
                      new DefaultFullHttpResponse(
                          HttpVersion.HTTP_1_1, HttpResponseStatus.OK))
                  .addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture inboundFuture) {
                      ctx.pipeline().remove(HttpProxyServerHandler.this);
                      ctx.pipeline().remove("HttpObjectAggregator");
                      ctx.pipeline().remove("HttpServerCodec");
                      ctx.pipeline().addLast(new RelayHandler(outboundFuture.channel()));
                    }
                  });
            } else {
              inboundChannel.write(new DefaultFullHttpResponse(
                  request.protocolVersion(), HttpResponseStatus.GATEWAY_TIMEOUT));
              ServerUtils.closeOnFlush(ctx.channel());
            }
          }
        });
      } else {
        final String domain = properties.getProperty("sp.domain");
        if (hostName.endsWith(domain)) {
          GSSManager manager = GSSManager.getInstance();
          try {
            GSSName serverName = manager
                .createName("HTTP/" + hostName + "@" + domain.toUpperCase(), null);
            Oid krb5Mechanism = new Oid("1.2.840.113554.1.2.2");
            GSSContext clientContext =
                manager.createContext(serverName, krb5Mechanism, null, GSSContext.DEFAULT_LIFETIME);
            byte[] clientToken = clientContext.initSecContext(new byte[0], 0, 0);
            String token = Base64.getEncoder().encodeToString(clientToken);
            request.headers().set("Authorization", "Negotiate " + token);
          } catch (GSSException e) {
            log.error("Fail to generate token", e);
          }
        }

        relayBootstrap.group(inboundChannel.eventLoop())
            .channel(NioSocketChannel.class)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .handler(new ChannelInitializer() {
              @Override
              protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG));
                ch.pipeline().addLast(new HttpClientCodec());
                ch.pipeline().addLast("HttpObjectAggregator", new HttpObjectAggregator(10485760));
                ch.pipeline().addLast(
                    "HttpProxyRelayHandler", new HttpProxyRelayHandler(inboundChannel));
              }
            });

        relayBootstrap.connect(hostName, port)
            .addListener((ChannelFutureListener) outboundFuture -> {
              if (outboundFuture.isSuccess()) {
                outboundFuture.channel().writeAndFlush(request);
              } else {
                inboundChannel.write(new DefaultFullHttpResponse(
                    request.protocolVersion(), HttpResponseStatus.GATEWAY_TIMEOUT));
                ServerUtils.closeOnFlush(ctx.channel());
              }
            });
      }
    } else {
      ctx.close();
    }
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) {
    ctx.flush();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    ServerUtils.closeOnFlush(ctx.channel());
  }
}
