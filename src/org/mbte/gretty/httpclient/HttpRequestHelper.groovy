package org.mbte.gretty.httpclient

import org.jboss.netty.channel.local.LocalAddress
import org.mbte.gretty.httpclient.GrettyClient
import groovypp.concurrent.BindLater
import org.jboss.netty.handler.codec.http.HttpResponse
import org.mbte.gretty.httpserver.GrettyHttpRequest
import org.mbte.gretty.httpserver.GrettyHttpResponse

@Trait abstract class HttpRequestHelper {

    void doTest (String request, Function1<HttpResponse,Void> action) {
        doTest([uri:request], action)
    }

    void doTest (GrettyHttpRequest request, Function1<GrettyHttpResponse,Void> action) {
        BindLater cdl = []

        GrettyClient client = [new LocalAddress("test_server")]
        client.connect{ future ->
            client.request(request) { bound ->
                try {
                    action(bound.get())
                    cdl.set("")
                }
                catch(Throwable e) {
                    cdl.setException(e)
                }
            }
        }

        cdl.get()
        client.disconnect ()
    }
}
