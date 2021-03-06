/*
 * Copyright 2013 Hewlett-Packard Development Company, L.P
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hp.alm.ali.rest.client;

import com.hp.alm.ali.Handler;
import com.hp.alm.ali.ServerVersion;
import com.hp.alm.ali.rest.client.exception.HttpClientErrorException;
import com.hp.alm.ali.rest.client.exception.HttpServerErrorException;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AliRestClientTest {

    private static Handler handler;

    @BeforeClass
    public static void startJetty() throws Exception {
        handler = new Handler(ServerVersion.AGM);
        handler.getServer().start();
    }

    @AfterClass
    public static void stopJetty() throws Exception {
        handler.getServer().stop();
    }

    @Before
    public void reset() {
        handler.clear();
    }

    @After
    public void done() throws Throwable {
        handler.finish();
    }

    @Test
    public void testRequireDomainWhenProjectSpecified_create() {
        try {
            AliRestClient.create("http://location", null, "project", "user", "password", RestClient.SessionStrategy.AUTO_LOGIN);
            Assert.fail("Domain is mandatory when project is specified.");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testRequireDomainWhenProjectSpecified_setDomain() {
        AliRestClient client = AliRestClient.create("http://location", "domain", "project", "user", "password", RestClient.SessionStrategy.AUTO_LOGIN);
        try {
            client.setDomain(null);
            Assert.fail("Domain is mandatory when project is specified.");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testRequireDomainWhenProjectSpecified_setProject() {
        AliRestClient client = AliRestClient.create("http://location", null, null, "user", "password", RestClient.SessionStrategy.AUTO_LOGIN);
        try {
            client.setProject("foo");
            Assert.fail("Domain is mandatory when project is specified.");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testLogin() throws Exception {
        handler.authenticate();

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), "domain", "project", "user", "password", RestClient.SessionStrategy.NONE);
        client.login();
    }

    @Test
    public void testLogin_Maya() throws Exception {
        handler.addRequest("POST", "/qcbin/authentication-point/alm-authenticate", 404)
                .responseBody("Not found");
        handler.addRequest("GET", "/qcbin/authentication-point/authenticate", 401)
                .responseHeader("WWW-Authenticate", "basic realm=\"alm realm\"")
                .responseBody("Unauthorized");
        handler.addRequest("GET", "/qcbin/authentication-point/authenticate", 200)
                .expectHeader("Authorization", "Basic dXNlcjpwYXNzd29yZA==");
        handler.addRequest("POST", "/qcbin/rest/site-session", 200)
                .expectBody("<session-parameters><client-type>ALI_IDEA_plugin</client-type></session-parameters>");

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), "domain", "project", "user", "password", RestClient.SessionStrategy.NONE);
        client.login();
    }

    @Test
    public void testLogin_ignoreNTLM() {
        handler.addRequest("POST", "/qcbin/authentication-point/alm-authenticate", 404)
                .responseBody("Not found");
        handler.addRequest("GET", "/qcbin/authentication-point/authenticate", 401)
                .responseHeader("WWW-Authenticate", "basic realm=\"alm realm\"")
                .responseHeader("WWW-Authenticate", "NTLM")
                .responseBody("Unauthorized");
        handler.addRequest("GET", "/qcbin/authentication-point/authenticate", 200)
                .expectHeader("Authorization", "Basic dXNlcjpwYXNzd29yZA==");
        handler.addRequest("POST", "/qcbin/rest/site-session", 200)
                .expectBody("<session-parameters><client-type>ALI_IDEA_plugin</client-type></session-parameters>");

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), "domain", "project", "user", "password", RestClient.SessionStrategy.NONE);
        client.login();
    }

    @Test
    public void testSessionStrategy_AUTO_LOGIN() throws Exception {
        handler.authenticate();
        handler.addRequest("GET", "/qcbin/rest/domains/domain/projects/project/test", 200);

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), "domain", "project", "user", "password", RestClient.SessionStrategy.AUTO_LOGIN);
        client.getForStream("/test");
    }

    @Test
    public void testSessionStrategy_AUTO_LOGIN_timeout() throws Exception {
        handler.authenticate();
        handler.addRequest("GET", "/qcbin/rest/domains/domain/projects/project/test", 403)
                .responseBody("Session expired.");
        handler.authenticate();
        handler.addRequest("GET", "/qcbin/rest/domains/domain/projects/project/test", 200);

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), "domain", "project", "user", "password", RestClient.SessionStrategy.AUTO_LOGIN);
        client.login();
        client.getForStream("/test");
    }

    @Test
    public void testSessionStrategy_NONE() throws Exception {
        handler.addRequest("GET", "/qcbin/rest/domains/domain/projects/project/test", 200);

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), "domain", "project", "user", "password", RestClient.SessionStrategy.NONE);
        client.getForStream("/test");
    }

    @Test
    public void testSetHttpProxy() throws Exception {
        handler.authenticate();
        handler.addRequest("GET", "/qcbin/rest/domains/domain/projects/project/test", 200);

        AliRestClient client = AliRestClient.create("http://foo/qcbin", "domain", "project", "user", "password", RestClient.SessionStrategy.AUTO_LOGIN);
        client.setHttpProxy("localhost", ((ServerConnector)handler.getServer().getConnectors()[0]).getLocalPort());
        client.getForStream("/test");
    }

    @Test
    public void testSetHttpProxyCredentials() throws Exception {
        handler.addRequest("POST", "/qcbin/authentication-point/alm-authenticate", 407)
                .responseHeader("Proxy-Authenticate", "Basic realm=\"proxy realm\"")
                .responseBody("Proxy Authentication Required");
        handler.addRequest("POST", "/qcbin/authentication-point/alm-authenticate", 200)
                .expectHeader("Proxy-Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ=")
                .expectBody("<alm-authentication><user>qc_user</user><password>qc_password</password></alm-authentication>");
        handler.addRequest("POST", "/qcbin/rest/site-session", 200)
                .expectBody("<session-parameters><client-type>ALI_IDEA_plugin</client-type></session-parameters>");
        handler.addRequest("GET", "/qcbin/rest/domains/domain/projects/project/test", 200);

        AliRestClient client = AliRestClient.create("http://foo/qcbin", "domain", "project", "qc_user", "qc_password", RestClient.SessionStrategy.AUTO_LOGIN);
        client.setHttpProxy("localhost", ((ServerConnector)handler.getServer().getConnectors()[0]).getLocalPort());
        client.setHttpProxyCredentials("username", "password");
        client.getForStream("/test");
    }

    @Test
    public void testNTLMEnabledProxy() throws Exception {
        handler.addRequest("POST", "/qcbin/authentication-point/alm-authenticate", 407)
                .responseHeader("Proxy-Authenticate", "Basic realm=\"proxy realm\"")
                .responseHeader("Proxy-Authenticate", "NTLM")
                .responseBody("Proxy Authentication Required");
        handler.addRequest("POST", "/qcbin/authentication-point/alm-authenticate", 200)
                .expectHeader("Proxy-Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ=")
                .expectBody("<alm-authentication><user>qc_user</user><password>qc_password</password></alm-authentication>");
        handler.addRequest("POST", "/qcbin/rest/site-session", 200)
                .expectBody("<session-parameters><client-type>ALI_IDEA_plugin</client-type></session-parameters>");
        handler.addRequest("GET", "/qcbin/rest/domains/domain/projects/project/test", 200);

        AliRestClient client = AliRestClient.create("http://foo/qcbin", "domain", "project", "qc_user", "qc_password", RestClient.SessionStrategy.AUTO_LOGIN);
        client.setHttpProxy("localhost", ((ServerConnector)handler.getServer().getConnectors()[0]).getLocalPort());
        client.setHttpProxyCredentials("username", "password");
        client.getForStream("/test");
    }

    @Test
    public void testGetForString() {
        handler.authenticate();
        handler.addRequest("GET", "/qcbin/rest/domains/domain/projects/project/path/arg1/arg2", 200)
                .responseBody("result");

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), "domain", "project", "user", "password", RestClient.SessionStrategy.AUTO_LOGIN);
        String result = client.getForString("/path/{0}/{1}", "arg1", "arg2");
        Assert.assertEquals("result", result);
    }

    @Test
    public void testGetForString_error() {
        handler.authenticate();
        handler.addRequest("GET", "/qcbin/rest/domains/domain/projects/project/path/arg1/arg2", 400)
                .reasonPhrase("bad request");

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), "domain", "project", "user", "password", RestClient.SessionStrategy.AUTO_LOGIN);
        try {
            client.getForString("/path/{0}/{1}", "arg1", "arg2");
            Assert.fail("HttpClientErrorException expected");
        } catch (HttpClientErrorException e) {
            Assert.assertEquals(400, e.getHttpStatus());
            Assert.assertEquals(handler.getServerUrl("/qcbin/rest/domains/domain/projects/project/path/arg1/arg2"), e.getLocation());
            Assert.assertEquals("bad request", e.getReasonPhrase());
        }
    }

    @Test
    public void testGetForStream() throws IOException {
        handler.authenticate();
        handler.addRequest("GET", "/qcbin/rest/domains/domain/projects/project/path/arg1/arg2", 200)
                .responseBody("result");

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), "domain", "project", "user", "password", RestClient.SessionStrategy.AUTO_LOGIN);
        InputStream result = client.getForStream("/path/{0}/{1}", "arg1", "arg2");
        Assert.assertEquals("result", IOUtils.toString(result));
    }

    @Test
    public void testGetForStream_error() {
        handler.authenticate();
        handler.addRequest("GET", "/qcbin/rest/domains/domain/projects/project/path/arg1/arg2", 500)
                .reasonPhrase("server failure");

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), "domain", "project", "user", "password", RestClient.SessionStrategy.AUTO_LOGIN);
        try {
            client.getForStream("/path/{0}/{1}", "arg1", "arg2");
            Assert.fail("HttpServerErrorException expected");
        } catch (HttpServerErrorException e) {
            Assert.assertEquals(500, e.getHttpStatus());
            Assert.assertEquals(handler.getServerUrl("/qcbin/rest/domains/domain/projects/project/path/arg1/arg2"), e.getLocation());
            Assert.assertEquals("server failure", e.getReasonPhrase());
        }
    }

    @Test
    public void testGet() {
        handler.authenticate();
        handler.addRequest("GET", "/qcbin/rest/domains/domain/projects/project/path/arg1/arg2", 200)
                .responseHeader("custom", "value")
                .responseBody("result");

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), "domain", "project", "user", "password", RestClient.SessionStrategy.AUTO_LOGIN);
        ResultInfo resultInfo = ResultInfo.create(new ByteArrayOutputStream());
        int code = client.get(resultInfo, "/path/{0}/{1}", "arg1", "arg2");
        Assert.assertEquals(200, code);
        Assert.assertEquals("result", resultInfo.getBodyStream().toString());
        Assert.assertEquals("value", resultInfo.getHeaders().get("custom"));
    }

    @Test
    public void testGet_loginError() {
        handler.addRequest("POST", "/qcbin/authentication-point/alm-authenticate", 500)
                .reasonPhrase("fatal error");

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), "domain", "project", "user", "password", RestClient.SessionStrategy.AUTO_LOGIN);
        ResultInfo resultInfo = ResultInfo.create(new ByteArrayOutputStream());
        int code = client.get(resultInfo, "/path/{0}/{1}", "arg1", "arg2");
        Assert.assertEquals(500, code);
        Assert.assertEquals("fatal error", resultInfo.getReasonPhrase());
        Assert.assertEquals(handler.getServerUrl("/qcbin/authentication-point/alm-authenticate [on-behalf-of: " +
                handler.getServerUrl("/qcbin/rest/domains/domain/projects/project/path/arg1/arg2") + "]"), resultInfo.getLocation());
    }

    @Test
    public void testGetForStream_loginError() {
        handler.addRequest("POST", "/qcbin/authentication-point/alm-authenticate", 400)
                .reasonPhrase("bad request");

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), "domain", "project", "user", "password", RestClient.SessionStrategy.AUTO_LOGIN);
        try {
            client.getForStream("/path/{0}/{1}", "arg1", "arg2");
            Assert.fail("HttpClientErrorException expected");
        } catch (HttpClientErrorException e) {
            Assert.assertEquals(400, e.getHttpStatus());
            Assert.assertEquals(handler.getServerUrl("/qcbin/authentication-point/alm-authenticate [on-behalf-of: " +
                    handler.getServerUrl("/qcbin/rest/domains/domain/projects/project/path/arg1/arg2") + "]"), e.getLocation());
            Assert.assertEquals("bad request", e.getReasonPhrase());
        }
    }

    @Test
    public void testPost() {
        handler.authenticate();
        handler.addRequest("POST", "/qcbin/rest/domains/domain/projects/project/path/arg1/arg2", 200)
                .expectHeader("header-input", "value-input")
                .expectBody("input")
                .responseHeader("header-output", "value-output")
                .responseBody("output");

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), "domain", "project", "user", "password", RestClient.SessionStrategy.AUTO_LOGIN);
        ResultInfo resultInfo = ResultInfo.create(new ByteArrayOutputStream());
        int code = client.post(InputData.create("input", Collections.singletonMap("header-input", "value-input")), resultInfo, "/path/{0}/{1}", "arg1", "arg2");
        Assert.assertEquals(200, code);
        Assert.assertEquals("output", resultInfo.getBodyStream().toString());
        Assert.assertEquals("value-output", resultInfo.getHeaders().get("header-output"));
    }

    @Test
    public void testPut() {
        handler.authenticate();
        handler.addRequest("PUT", "/qcbin/rest/domains/domain/projects/project/path/arg1/arg2", 200)
                .expectHeader("header-input", "value-input")
                .expectBody("input")
                .responseHeader("header-output", "value-output")
                .responseBody("output");

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), "domain", "project", "user", "password", RestClient.SessionStrategy.AUTO_LOGIN);
        ResultInfo resultInfo = ResultInfo.create(new ByteArrayOutputStream());
        int code = client.put(InputData.create("input", Collections.singletonMap("header-input", "value-input")), resultInfo, "/path/{0}/{1}", "arg1", "arg2");
        Assert.assertEquals(200, code);
        Assert.assertEquals("output", resultInfo.getBodyStream().toString());
        Assert.assertEquals("value-output", resultInfo.getHeaders().get("header-output"));
    }

    @Test
    public void testListDomains() {
        handler.authenticate();
        handler.addRequest("GET", "/qcbin/rest/domains", 200)
                .responseBody("<Domains>" +
                        " <Domain Name='emea'/>" +
                        " <Domain Name='asia'/>" +
                        " <Domain Name='pacific'/>" +
                        "</Domains>");

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), null, null, "user", "password", RestClient.SessionStrategy.AUTO_LOGIN);
        List<String> domains = client.listDomains();
        Assert.assertEquals(Arrays.asList("emea", "asia", "pacific"), domains);
    }

    @Test
    public void testListCurrentProjects() {
        handler.authenticate();
        handler.addRequest("GET", "/qcbin/rest/domains/emea/projects", 200)
                .responseBody("<Projects>" +
                        " <Project Name='first'/>" +
                        " <Project Name='second'/>" +
                        " <Project Name='third'/>" +
                        "</Projects>");

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), "emea", null, "user", "password", RestClient.SessionStrategy.AUTO_LOGIN);
        List<String> projects = client.listCurrentProjects();
        Assert.assertEquals(Arrays.asList("first", "second", "third"), projects);
    }

    @Test
    public void testListCurrentProjects_noDomain() {
        AliRestClient client = AliRestClient.create(handler.getQcUrl(), null, null, "user", "password", RestClient.SessionStrategy.AUTO_LOGIN);
        try {
            client.listCurrentProjects();
            Assert.fail("IllegalStateException expected");
        } catch (IllegalStateException e) {
            // domain not selected
        }
    }

    @Test
    public void testSetEncoding_utf8() {
        handler.addRequest("GET", "/qcbin/rest/domains/domain/projects/project/abc_%C5%BElu%C5%A5ou%C4%8Dk%C3%BD%20k%C5%AF%C5%88%20def", 200);

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), "domain", "project", "user", "password", RestClient.SessionStrategy.NONE);
        client.setEncoding("UTF-8");
        client.getForString("abc_{0}{1}def", "\u017Elu\u0165ou\u010Dk\u00FD k\u016F\u0148", " ");
    }

    @Test
    public void testSetEncoding_none() {
        handler.addRequest("GET", "/qcbin/rest/domains/domain/projects/project/abc_%C5%BElu%C5%A5ou%C4%8Dk%C3%BD%20k%C5%AF%C5%88+def", 200);

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), "domain", "project", "user", "password", RestClient.SessionStrategy.NONE);
        client.setEncoding(null);
        client.getForString("abc_{0}{1}def", "%C5%BElu%C5%A5ou%C4%8Dk%C3%BD%20k%C5%AF%C5%88", "+");
    }

    @Test
    public void testTicketParsing() {
        handler.addRequest("GET", "/qcbin/rest/domains/domain/projects/project/abc", 500)
                .responseHeader("Content-type", "text/html;charset=ISO-8859-1")
                .content("error_ticket.html");

        AliRestClient client = AliRestClient.create(handler.getQcUrl(), "domain", "project", "user", "password", RestClient.SessionStrategy.NONE);
        try {
            client.getForString("abc");
        } catch (HttpServerErrorException e) {
            Assert.assertEquals("85449334-9650-4b67-96f6-b491e18a74c0", e.getErrorCode());
        }
    }
}
