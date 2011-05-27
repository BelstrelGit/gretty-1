/*
 * Copyright 2009-2010 MBTE Sweden AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mbte.gretty.httpclient

import org.jboss.netty.handler.codec.http.HttpResponse

import groovypp.concurrent.BindLater
import org.jboss.netty.handler.codec.http.HttpRequest

import org.jboss.netty.channel.ChannelHandlerContext

import org.jboss.netty.channel.MessageEvent

import org.mbte.gretty.httpserver.GrettyHttpResponse
import org.jboss.netty.channel.ChannelFactory
import org.jboss.netty.channel.ChildChannelStateEvent
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.jboss.netty.channel.ChannelStateEvent
import org.jboss.netty.channel.ExceptionEvent
import java.util.concurrent.Executor
import org.jboss.netty.handler.codec.http.HttpHeaders
import org.mbte.gretty.httpserver.GrettyHttpRequest
import org.jboss.netty.handler.codec.http.CookieDecoder
import org.jboss.netty.handler.codec.http.Cookie
import org.jboss.netty.handler.codec.http.CookieEncoder
import java.nio.channels.ClosedChannelException

@Typed class GrettyClient extends AbstractHttpClient {

    static class ResponseResult extends BindLater<GrettyHttpResponse> {
        volatile long written
        volatile long received
    }

    protected volatile Pair<GrettyHttpRequest, ResponseResult> pendingRequest

    GrettyClient(SocketAddress remoteAddress, ChannelFactory factory = null) {
        super(remoteAddress, factory)
    }

    void request(GrettyHttpRequest request, Executor executor = null, BindLater.Listener<GrettyHttpResponse> action) {
        requestInternal request, (ResponseResult)new ResponseResult().whenBound(executor, action)
    }

    ResponseResult request(GrettyHttpRequest request) {
        requestInternal request, []
    }

    private ResponseResult requestInternal (GrettyHttpRequest request, ResponseResult later) {
        assert pendingRequest.compareAndSet(null, [request, later])
        channel.write(request).addListener { later.written = System.nanoTime() }
        return later
    }

    void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
        GrettyHttpResponse resp = e.message
        def pending = pendingRequest.getAndSet(null)
        if(pending.first.followRedirects() && (resp.status == HttpResponseStatus.FOUND || resp.status == HttpResponseStatus.MOVED_PERMANENTLY)) {
            URL url = [resp.getHeader(HttpHeaders.Names.LOCATION)]

            def redirectAddress = new InetSocketAddress(url.host, url.port != -1 ? url.port : 80)
            def req = pending.first
            req.uri = "$url.path${url.query ? '?' + url.query : ''}"
            if(req.uri == '/')
                req.uri = ''
            def cookies = resp.getHeaders(HttpHeaders.Names.SET_COOKIE)
            if(cookies) {
                def oldCookies = req.getHeaders(HttpHeaders.Names.COOKIE)
                Set<Cookie> newCookies = []
                for(oldCookie in oldCookies) {
                    newCookies.addAll(new CookieDecoder().decode(oldCookie))
                }
                req.removeHeader(HttpHeaders.Names.COOKIE)
                for(cookie in cookies) {
                    newCookies.addAll(new CookieDecoder().decode(cookie))
                }
                for(newCookie in newCookies) {
                    def encoder = new CookieEncoder(false)
                    encoder.addCookie newCookie
                    req.addHeader(HttpHeaders.Names.COOKIE, encoder.encode())
                }
            }
            if(redirectAddress != remoteAddress) {
                GrettyClient redirectClient = [redirectAddress, channelFactory]
                redirectClient.connect() { future ->
                    redirectClient.requestInternal req, pending.second
                }
            }
            else {
                requestInternal(req, pending.second)
            }
        }
        else {
            pending.second.received = System.nanoTime()
            pending.second.set(resp)
        }
    }

    void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
        super.channelClosed(ctx, e)

        def pending = pendingRequest.getAndSet(null)
        pending?.second?.setException(new ClosedChannelException())
    }

    @Override
    void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        def pending = pendingRequest.getAndSet(null)
        pending?.second?.setException(e.cause)
        channel?.close()
    }
}
