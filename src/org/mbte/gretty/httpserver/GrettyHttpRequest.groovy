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

package org.mbte.gretty.httpserver

import org.jboss.netty.handler.codec.http.*
import org.jboss.netty.handler.codec.base64.Base64
import org.jboss.netty.buffer.ChannelBuffers

@Typed class GrettyHttpRequest extends DefaultHttpRequest {

    private String path
    private Map<String, List<String>> params
    private boolean followRedirects = false

    public GrettyHttpRequest(HttpVersion httpVersion = HttpVersion.HTTP_1_1, HttpMethod method = HttpMethod.GET, String uri) {
        super(httpVersion, method, uri)
    }

    void setUri(String uri) {
        super.setUri(uri)

        path = null
        params = null
    }

    String getPath () {
        if(path == null) {
            def pathEndPos = uri.indexOf('?')
            path = pathEndPos < 0 ? uri : uri.substring(0, pathEndPos)
        }
        path
    }

    Map<String, List<String>> getParameters() {
        if(params == null)
            params = new QueryStringDecoder(uri).parameters
        params
    }
    
    void setParameters(Map<String,String> params) {
//        def encoder = new QueryStringEncoder(decoder.path)
//        for(param in params) {
//            encoder.addParam param.key, v
//        }
    }

    String getContentText () {
        new String(content.array(), content.arrayOffset(), content.readableBytes())
    }

    GrettyHttpRequest setAuthorization (String user, String password) {
        def line = "$user:$password"
        def cb = ChannelBuffers.wrappedBuffer(line.bytes)
        cb = Base64.encode(cb)
        line = new String(cb.array(), cb.arrayOffset(), cb.readableBytes())
        setHeader(HttpHeaders.Names.AUTHORIZATION, "Basic $line")
        this
    }

    GrettyHttpRequest keepAlive () {
        setHeader HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE
        this
    }

    GrettyHttpRequest cookies (Map<String,String> cookies) {
        def encoder = new CookieEncoder(false)
        for(e in cookies.entrySet())
            encoder.addCookie(e.key, e.value)
        setHeader(HttpHeaders.Names.COOKIE, encoder.encode())
        this
    }

    GrettyHttpRequest followRedirects (boolean follow) {
        followRedirects = follow
        this
    }

    boolean followRedirects () {
        followRedirects
    }
}
