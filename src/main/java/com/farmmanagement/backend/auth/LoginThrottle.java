package com.farmmanagement.backend.auth;

import com.farmmanagement.backend.common.Api;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/** Direct LAN clients are keyed by socket address; untrusted forwarded headers are ignored. */
public final class LoginThrottle extends OncePerRequestFilter {
    private record Window(long start,int count) {}
    private final ConcurrentHashMap<String,Window> clients=new ConcurrentHashMap<>();
    private final Api api;private final int limit;private final Clock clock;
    public LoginThrottle(Api api,int limit,Clock clock) { this.api=api;this.limit=limit;this.clock=clock; }
    @Override protected boolean shouldNotFilter(HttpServletRequest r) {
        String path=r.getRequestURI().substring(r.getContextPath().length());
        return !r.getMethod().equals("POST") || !(path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register") || path.equals("/api/v1/store/orders"));
    }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        long now=clock.millis();String address=req.getRemoteAddr();
        if(clients.size()>=10000) {
            clients.entrySet().removeIf(e->now-e.getValue().start>=60000);
            if(clients.size()>=10000 && !clients.containsKey(address)) { deny(req,res);return; }
        }
        Window window=clients.compute(address,(key,current)->current==null || now-current.start>=60000?new Window(now,1):new Window(current.start,Math.min(limit+1,current.count+1)));
        if(window.count>limit) { deny(req,res);return; }
        chain.doFilter(req,res);
    }
    private void deny(HttpServletRequest req,HttpServletResponse res) throws IOException {
        res.setHeader("Retry-After","60");api.write(req,res,429,"RATE_LIMITED","Quá nhiều yêu cầu. Vui lòng thử lại sau một phút.");
    }
}
