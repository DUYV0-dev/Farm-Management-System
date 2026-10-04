package com.farmmanagement.backend.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    private final Api api;
    public RequestIdFilter(Api api) { this.api=api; }
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        req.setAttribute("requestId",id); res.setHeader("X-Request-Id",id);
        res.setHeader("Cache-Control","no-store");
        try { chain.doFilter(req,res); }
        catch (org.springframework.dao.DataAccessException ex) {
            org.slf4j.LoggerFactory.getLogger(RequestIdFilter.class).error("requestId={} authentication database unavailable",id);
            if(res.isCommitted()) throw ex;
            api.write(req,res,503,"SERVICE_UNAVAILABLE","Database tạm thời không sẵn sàng.");
        }
    }
}
