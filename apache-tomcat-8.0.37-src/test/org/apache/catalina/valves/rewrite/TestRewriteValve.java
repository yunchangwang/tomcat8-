/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.valves.rewrite;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestRewriteValve extends TomcatBaseTest {

    @Test
    public void testNoRewrite() throws Exception {
        doTestRewrite("", "/a/%255A", "/a/%255A");
    }

    @Test
    public void testBackslashPercentSign() throws Exception {
        doTestRewrite("RewriteRule ^(.*) /a/\\%5A", "/", "/a/%255A");
    }

    @Test
    public void testNoopRewrite() throws Exception {
        doTestRewrite("RewriteRule ^(.*) $1", "/a/%255A", "/a/%255A");
    }

    @Test
    public void testPathRewrite() throws Exception {
        doTestRewrite("RewriteRule ^/b(.*) /a$1", "/b/%255A", "/a/%255A");
    }

    @Test
    public void testNonNormalizedPathRewrite() throws Exception {
        doTestRewrite("RewriteRule ^/b/(.*) /b/../a/$1", "/b/%255A", "/b/../a/%255A");
    }

    // BZ 57863
    @Test
    public void testRewriteMap01() throws Exception {
        doTestRewrite("RewriteMap mapa org.apache.catalina.valves.rewrite.TesterRewriteMapA\n" +
                "RewriteRule /b/(.*).html$ /c/${mapa:$1}", "/b/a.html", "/c/aa");
    }

    @Test
    public void testRewriteMap02() throws Exception {
        doTestRewrite("RewriteMap mapa org.apache.catalina.valves.rewrite.TesterRewriteMapA\n" +
                "RewriteRule /b/(.*).html$ /c/${mapa:$1|dd}", "/b/x.html", "/c/dd");
    }

    @Test
    public void testRewriteServerVar() throws Exception {
        doTestRewrite("RewriteRule /b/(.*).html$ /c%{SERVLET_PATH}", "/b/x.html", "/c/b/x.html");
    }

    @Test
    public void testRewriteEnvVarAndServerVar() throws Exception {
        System.setProperty("some_variable", "something");
        doTestRewrite("RewriteRule /b/(.*).html$ /c/%{ENV:some_variable}%{SERVLET_PATH}", "/b/x.html", "/c/something/b/x.html");
    }

    @Test
    public void testRewriteServerVarAndEnvVar() throws Exception {
        System.setProperty("some_variable", "something");
        doTestRewrite("RewriteRule /b/(.*).html$ /c%{SERVLET_PATH}/%{ENV:some_variable}", "/b/x.html", "/c/b/x.html/something");
    }

    @Test
    public void testRewriteMissingCurlyBraceOnVar() throws Exception {
        try {
            doTestRewrite("RewriteRule /b/(.*).html$ /c%_{SERVLET_PATH}", "/b/x.html", "/c");
            Assert.fail("IAE expected.");
        } catch (java.lang.IllegalArgumentException e) {
            // excpected as %_{ is invalid
        }
    }

    @Test
    public void testRewriteMissingCurlyBraceOnMapper() throws Exception {
        try {
            doTestRewrite("RewriteRule /b/(.*).html$ /c$_{SERVLET_PATH}", "/b/x.html", "/c");
            Assert.fail("IAE expected.");
        } catch (java.lang.IllegalArgumentException e) {
            // excpected as $_{ is invalid
        }
    }

    // https://bz.apache.org/bugzilla/show_bug.cgi?id=60013
    public void testRewriteWithEncoding02() throws Exception {
        doTestRewrite("RewriteRule ^/b/(.*)$ /c/?param=$1 [L]",
                "/b/%E5%9C%A8%E7%BA%BF%E6%B5%8B%E8%AF%95", "/c/", "param=\u5728\u7EBF\u6D4B\u8BD5");
    }

    private void doTestRewrite(String config, String request, String expectedURI) throws Exception {
        doTestRewrite(config, request, expectedURI, null);
    }

    @Test
    public void testNonAsciiPath() throws Exception {
        doTestRewrite("RewriteRule ^/b/(.*) /c/$1", "/b/%E5%9C%A8%E7%BA%BF%E6%B5%8B%E8%AF%95",
                "/c/%E5%9C%A8%E7%BA%BF%E6%B5%8B%E8%AF%95");
    }

    @Test
    public void testNonAsciiPathRedirect() throws Exception {
        doTestRewrite("RewriteRule ^/b/(.*) /c/$1 [R]",
                "/b/%E5%9C%A8%E7%BA%BF%E6%B5%8B%E8%AF%95",
                "/c/%E5%9C%A8%E7%BA%BF%E6%B5%8B%E8%AF%95");
    }

    @Test
    public void testQueryString() throws Exception {
        doTestRewrite("RewriteRule ^/b/(.*) /c?$1", "/b/id=1", "/c", "id=1");
    }

    @Test
    public void testNonAsciiQueryString() throws Exception {
        doTestRewrite("RewriteRule ^/b/(.*) /c?$1", "/b/id=%E5%9C%A8%E7%BA%BF%E6%B5%8B%E8%AF%95",
                "/c", "id=%E5%9C%A8%E7%BA%BF%E6%B5%8B%E8%AF%95");
    }


    @Test
    public void testNonAsciiQueryStringAndRedirectPath() throws Exception {
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/$1?$2", "/b/%E5%9C%A8%E7%BA%BF/id=%E6%B5%8B%E8%AF%95",
                "/c/%E5%9C%A8%E7%BA%BF", "id=%E6%B5%8B%E8%AF%95");
    }


    private void doTestRewrite(String config, String request, String expectedURI,
            String expectedQueryString) throws Exception {

        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        RewriteValve rewriteValve = new RewriteValve();
        ctx.getPipeline().addValve(rewriteValve);

        rewriteValve.setConfiguration(config);

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/a/%5A", "snoop");
        ctx.addServletMappingDecoded("/c/*", "snoop");
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + request);
        res.setCharset(StandardCharsets.UTF_8);

        String body = res.toString();
        RequestDescriptor requestDesc = SnoopResult.parse(body);
        String requestURI = requestDesc.getRequestInfo("REQUEST-URI");
        Assert.assertEquals(expectedURI, requestURI);

        if (expectedQueryString != null) {
            String queryString = requestDesc.getRequestInfo("REQUEST-QUERY-STRING");
            Assert.assertEquals(expectedQueryString, queryString);
        }
    }
}
