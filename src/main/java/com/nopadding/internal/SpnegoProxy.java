package com.nopadding.internal;

import com.nopadding.internal.handler.HttpProxyServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
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

  private static final String PROP_FILE_NAME = "application.properties";

  private final EventLoopGroup eventLoop;
  private final Properties properties;

  /**
   * Constructor.
   */
  public SpnegoProxy(Properties properties) {
    this.properties = properties;
    eventLoop = new NioEventLoopGroup();
  }

  /**
   * Common run.
   */
  public void run() throws InterruptedException {
    System.setProperty("java.security.krb5.kdc",
        properties.getProperty("java.security.krb5.kdc"));
    System.setProperty("java.security.krb5.realm",
        properties.getProperty("java.security.krb5.realm"));

    final String bindAddress = properties.getProperty(SpnegoProxyConstant.SP_BIND_ADDRESS);
    final int port = Integer.parseInt(properties.getProperty(SpnegoProxyConstant.SP_PORT));

    ServerBootstrap serverBootstrap = new ServerBootstrap();
    serverBootstrap.group(eventLoop);
    serverBootstrap.channel(NioServerSocketChannel.class);
    serverBootstrap.handler(new LoggingHandler(LogLevel.DEBUG));
    serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
      @Override
      protected void initChannel(SocketChannel ch) {
        ch.pipeline().addLast("HttpRequestDecoder", new HttpRequestDecoder());
        ch.pipeline().addLast(new HttpProxyServerHandler(properties));
      }
    });
    serverBootstrap.bind(new InetSocketAddress(bindAddress, port)).sync();
  }

  private void shutdown() {
    eventLoop.shutdownGracefully();
  }

  private static CommandLine parseCmd(String[] args) throws ParseException {
    CommandLineParser parser = new DefaultParser();
    Options options = new Options();
    options.addOption(new Option("c", "config", true, "Config location."));
    return parser.parse(options, args);
  }

  /**
   * Main method.
   *
   * @param args -p application.properties
   */
  public static void main(String[] args)
      throws InterruptedException, IOException {
    CommandLine cmd;
    String propFile = null;
    try {
      cmd = parseCmd(args);
      propFile = cmd.getOptionValue('c');
    } catch (ParseException e) {
      e.printStackTrace();
    }
    Properties properties = new Properties();
    if (propFile == null) {
      properties.load(SpnegoProxy.class.getClassLoader().getResourceAsStream(PROP_FILE_NAME));
    } else {
      properties.load(new FileReader(propFile));
    }
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
