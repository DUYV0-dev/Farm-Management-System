package com.farmmanagement.backend.config;

import com.farmmanagement.backend.auth.LoginThrottle;
import com.farmmanagement.backend.common.Api;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP requests exercise the native Tomcat valve before the actual throttle. */
class ProxyDeploymentTests {
    @TestConfiguration(proxyBeanMethods=false)
    @Import({TomcatServletWebServerAutoConfiguration.class,com.farmmanagement.backend.common.RequestIdFilter.class})
    static class WebApp {
        @Bean Api api() { return new Api(new tools.jackson.databind.ObjectMapper()); }
        @Bean LoginThrottle throttle(Api api) {
            return new LoginThrottle(api,1,Clock.systemUTC());
        }
        @Bean ServletRegistrationBean<HttpServlet> echo() {
            return new ServletRegistrationBean<>(new HttpServlet() {
                @Override protected void doPost(HttpServletRequest req,HttpServletResponse res) throws IOException {
                    res.getWriter().write(req.getRemoteAddr()+"|"+req.isSecure());
                }
            },"/*");
        }
    }
    ServletWebServerApplicationContext start(String strategy,String proxies) {
        return (ServletWebServerApplicationContext)new SpringApplicationBuilder(WebApp.class)
            .web(WebApplicationType.SERVLET).run("--server.port=0","--server.address=127.0.0.1",
                "--server.ssl.enabled=false","--FORWARD_HEADERS_STRATEGY="+strategy,
                "--TRUSTED_PROXY_REGEX="+proxies,"--spring.main.banner-mode=off",
                "--logging.file.name=target/proxy-deployment-tests.log");
    }
    HttpResponse<String> post(HttpClient client,ServletWebServerApplicationContext context,String path,String ip) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+context.getWebServer().getPort()+path))
            .timeout(Duration.ofSeconds(10)).header("X-Forwarded-For",ip).header("X-Forwarded-Proto","https")
            .POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void trustedProxyPreservesSeparateClientLimitsAndHttps() throws Exception {
        try(var context=start("native","127[.]0[.]0[.]1");var client=HttpClient.newHttpClient()) {
            var a=post(client,context,"/api/v1/auth/login","198.51.100.11");
            assertEquals(200,a.statusCode());assertEquals("198.51.100.11|true",a.body());
            assertEquals(200,post(client,context,"/api/v1/auth/register","198.51.100.12").statusCode());
            var limited=post(client,context,"/api/v1/store/orders","198.51.100.11");
            assertEquals(429,limited.statusCode());assertEquals("60",limited.headers().firstValue("Retry-After").orElseThrow());
            // A claimed earlier hop does not turn a known client into another IP.
            assertEquals(429,post(client,context,"/api/v1/auth/login","203.0.113.99, 198.51.100.11").statusCode());
        }
    }
    @Test void untrustedSocketPeerCannotSpoofClientIpOrHttps() throws Exception {
        try(var context=start("native","192[.]0[.]2[.]10");var client=HttpClient.newHttpClient()) {
            var first=post(client,context,"/api/v1/auth/login","198.51.100.11");
            assertEquals(200,first.statusCode());assertEquals("127.0.0.1|false",first.body());
            assertEquals(429,post(client,context,"/api/v1/auth/login","198.51.100.12").statusCode());
        }
    }
    @Test void directModeIgnoresForwardedHeadersEvenFromLoopback() throws Exception {
        try(var context=start("none","127[.]0[.]0[.]1");var client=HttpClient.newHttpClient()) {
            var first=post(client,context,"/api/v1/auth/login","198.51.100.11");
            assertEquals("127.0.0.1|false",first.body());
            assertEquals(429,post(client,context,"/api/v1/auth/login","198.51.100.12").statusCode());
        }
    }
}
