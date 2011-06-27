/*
 * Copyright 2009-2010 MBTE Sweden AB.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.mbte.gretty

import java.util.concurrent.Executors
import java.util.concurrent.Executor

import org.jboss.netty.channel.group.DefaultChannelGroup
import org.jboss.netty.channel.Channel

import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.channel.local.DefaultLocalServerChannelFactory
import org.jboss.netty.channel.local.LocalAddress
import org.jboss.netty.channel.ChannelPipeline

import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.ChannelStateEvent
import org.jboss.netty.channel.Channels
import org.jboss.netty.channel.ChannelPipelineFactory
import java.util.concurrent.ExecutorService
import org.mbte.gretty.httpserver.IoMonitor
import org.jboss.netty.logging.InternalLogLevel
import org.jboss.netty.logging.InternalLoggerFactory
import org.jboss.netty.logging.InternalLogger

@Typed abstract class AbstractServer<OwnType> extends BaseChannelHandler<OwnType> implements Executor {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractServer)

    final IoMonitor ioMonitor = []

    int              ioWorkerCount      = 2*Runtime.getRuntime().availableProcessors()
    int              serviceWorkerCount = 4*Runtime.getRuntime().availableProcessors()

    final DefaultChannelGroup allConnected = []

    protected ExecutorService threadPool

    void start () {
        def bossExecutor = Executors.newCachedThreadPool()
        def ioExecutor   = Executors.newFixedThreadPool(ioWorkerCount)
        threadPool       = Executors.newFixedThreadPool(serviceWorkerCount)

        if(!localAddress) {
            throw new IllegalStateException("localAddress is not configured")
        }

        def isLocal = localAddress instanceof LocalAddress

        def channelFactory = isLocal ? new DefaultLocalServerChannelFactory () : (NioServerSocketChannelFactory )[bossExecutor, ioExecutor]

        ServerBootstrap bootstrap = [factory: channelFactory, pipelineFactory:this]
        bootstrap.setOption("child.tcpNoDelay", true)
        bootstrap.setOption("child.keepAlive",  true)

        channel = bootstrap.bind(localAddress)
        channel.closeFuture.addListener {
            [bossExecutor, ioExecutor, threadPool]*.shutdown()
        }

        if(logger.isInfoEnabled())
            logger.info("Started server on $localAddress")
    }

    void stop() {
        if(logger.isInfoEnabled())
            logger.info("Stopping server on $localAddress")

        allConnected.close().awaitUninterruptibly()
        channel.close().awaitUninterruptibly()
    }

    void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
        if(logger.isEnabled(InternalLogLevel.DEBUG))
            logger.debug("${ctx.channel} connected")

        allConnected.add(ctx.channel)
        super.channelConnected(ctx, e)
    }

    void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
        if(logger.isEnabled(InternalLogLevel.DEBUG))
            logger.debug("${ctx.channel} disconnected")

        super.channelDisconnected(ctx, e)
    }

    void execute(Runnable command) {
        threadPool.execute command
    }

    protected void buildPipeline(ChannelPipeline pipeline) {
        super.buildPipeline(pipeline)
        pipeline.addFirst ("ioMonitor", ioMonitor)
    }

    ExecutorService getThreadPool () {
        this.threadPool
    }

    void setLogStatistics(boolean log) {
        ioMonitor.logStatistics = log
    }

    OwnType logStatistics(boolean log) {
        this[logStatistics: log]
    }

    OwnType ioWorkerCount(int ioWorkerCount) {
        this[ioWorkerCount: ioWorkerCount]
    }

    OwnType serviceWorkerCount(int serviceWorkerCount) {
        this[serviceWorkerCount: serviceWorkerCount]
    }
}
