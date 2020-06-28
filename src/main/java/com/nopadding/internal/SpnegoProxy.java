package com.nopadding.internal;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

@Slf4j
public class SpnegoProxy {

  private EventLoopGroup eventLoop;
  private final Properties properties;

  /**
   * Constructor.
   */
  public SpnegoProxy(Properties properties) {
    this.properties = properties;
    System.setProperty(
        "javax.security.auth.useSubjectCredsOnly", "false");
    System.setProperty("java.security.auth.login.config", properties.getProperty("sp.jaas.path"));
  }

  /**
   * Common run.
   */
  public void run() throws InterruptedException {
    final String bindAddress = properties.getProperty("sp.bind.address");
    final int port = Integer.parseInt(properties.getProperty("sp.port"));
    this.eventLoop = new NioEventLoopGroup();
    ServerBootstrap serverBootstrap = new ServerBootstrap();
    serverBootstrap.group(eventLoop);
    serverBootstrap.channel(NioServerSocketChannel.class);
    serverBootstrap.handler(new LoggingHandler(LogLevel.DEBUG));

    serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
      @Override
      protected void initChannel(SocketChannel ch) {
        ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG));
        ch.pipeline().addLast("HttpServerCodec", new HttpServerCodec());
        ch.pipeline().addLast("HttpObjectAggregator", new HttpObjectAggregator(10485760));
        ch.pipeline().addLast(new HttpProxyServerHandler(properties));
      }
    });
    ChannelFuture channelFuture = serverBootstrap
        .bind(new InetSocketAddress(bindAddress, port)).sync();
    channelFuture.channel().closeFuture().sync();
  }

  private void shutdown() throws InterruptedException {
    eventLoop.shutdownGracefully();
  }

  private static CommandLine parseCmd(String[] args) throws ParseException {
    CommandLineParser parser = new DefaultParser();
    Options options = new Options();
    options.addOption(new Option("p", "prop", true, "Properties file localtion."));
    return parser.parse(options, args);
  }

  /**
   * Main method.
   *
   * @param args -p application.properties
   */
  public static void main(String[] args)
      throws InterruptedException, ParseException, IOException {
    CommandLine cmd = parseCmd(args);
    String propFile = cmd.getOptionValue('p');
    Properties properties = new Properties();
    properties.load(new FileReader(propFile));
    SpnegoProxy mainObj = new SpnegoProxy(properties);
    Runtime.getRuntime().addShutdownHook(new Thread("shutdown-hook") {
      @Override
      public void run() {
        try {
          log.info("Shutting down...");
          mainObj.shutdown();
        } catch (Exception e) {
          log.error("Fail to shutdown", e);
        }
      }
    });
    mainObj.run();
  }
}
