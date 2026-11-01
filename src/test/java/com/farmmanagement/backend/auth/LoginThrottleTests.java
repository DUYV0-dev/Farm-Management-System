package com.farmmanagement.backend.auth;
import com.farmmanagement.backend.common.Api;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
class LoginThrottleTests {
    @Test void rejectsRepeatedAuthenticationButDoesNotTrustSpoofedForwardedAddresses() throws Exception {
        var filter=new LoginThrottle(new Api(new tools.jackson.databind.ObjectMapper()),1,Clock.systemUTC());
        var first=new MockHttpServletRequest("POST","/api/v1/auth/login");first.setRemoteAddr("192.168.1.5");
        filter.doFilter(first,new MockHttpServletResponse(),(q,s)->{});
        var next=new MockHttpServletRequest("POST","/api/v1/auth/login");next.setRemoteAddr("192.168.1.5");next.addHeader("X-Forwarded-For","different");
        next.setAttribute("requestId","throttle-test");
        var response=new MockHttpServletResponse();filter.doFilter(next,response,(q,s)->fail("Should be throttled"));
        assertEquals(429,response.getStatus());assertEquals("60",response.getHeader("Retry-After"));
    }
    @Test void isolatesClientsAndDoesNotThrottleBusinessRequests() throws Exception {
        var filter=new LoginThrottle(new Api(new tools.jackson.databind.ObjectMapper()),1,Clock.systemUTC());
        for(String ip:java.util.List.of("192.168.1.5","192.168.1.6")) {
            var request=new MockHttpServletRequest("POST","/api/v1/auth/login");request.setRemoteAddr(ip);
            filter.doFilter(request,new MockHttpServletResponse(),(q,s)->{});
        }
        var business=new MockHttpServletRequest("POST","/api/v1/operations/tasks");business.setRemoteAddr("192.168.1.5");
        filter.doFilter(business,new MockHttpServletResponse(),(q,s)->{});
    }
}
