package com.nopadding.internal.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpProxyRelayHandler extends ChannelInboundHandlerAdapter {

  private final Channel clientChannel;

  HttpProxyRelayHandler(Channel clientChannel) {
    this.clientChannel = clientChannel;
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (clientChannel.isActive()) {
      clientChannel.writeAndFlush(msg);
    } else {
      ReferenceCountUtil.release(msg);
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) {
    if (clientChannel.isActive()) {
      ServerUtils.closeOnFlush(clientChannel);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.error("Replay channel exception.", cause);
    ctx.close();
  }
}
