package com.farmmanagement.backend.tenancy;

import com.farmmanagement.backend.common.Api;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Every API request uses one transaction; PostgreSQL tenant settings are local
 * to that transaction and cannot leak through a pooled connection. */
public class FarmContextFilter extends OncePerRequestFilter {
    private final FarmWorkspaceStore store;
    private final TransactionTemplate tx;
    private final Api api;
    public FarmContextFilter(FarmWorkspaceStore store,TransactionTemplate tx,Api api) { this.store=store;this.tx=tx;this.api=api; }
    private String path(HttpServletRequest req) { return req.getRequestURI().substring(req.getContextPath().length()); }
    @Override protected boolean shouldNotFilter(HttpServletRequest req) { return !path(req).startsWith("/api/v1/"); }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        try {
            tx.executeWithoutResult(status->{
                try {
                    var auth=SecurityContextHolder.getContext().getAuthentication();
                    if(auth instanceof JwtAuthenticationToken jwt) {
                        int uid=Integer.parseInt(jwt.getToken().getSubject());
                        String header=req.getHeader("X-Farm-Id");
                        Integer farm=null;
                        var memberships=store.memberships(uid);
                        var active=memberships.stream().filter(m->m.status().equals("ACTIVE") && m.farmStatus().equals("ACTIVE")).toList();
                        if(header!=null) {
                            try { farm=Integer.valueOf(header); if(farm<=0) throw new NumberFormatException(); }
                            catch(NumberFormatException ex) { api.write(req,res,422,"VALIDATION_ERROR","Mã trang trại không hợp lệ.");return; }
                        } else if(active.size()==1) farm=active.getFirst().farmId();
                        FarmWorkspaceStore.Membership selected=null;
                        if(farm!=null) {
                            int id=farm;
                            selected=active.stream().filter(m->m.farmId()==id).findFirst().orElse(null);
                            if(selected==null) { api.write(req,res,403,"FORBIDDEN","Bạn không thuộc trang trại này.");return; }
                        }
                        store.context(uid,farm);
                        var authorities=new ArrayList<>(jwt.getAuthorities());
                        if(selected!=null) {
                            selected.permissions().forEach(p->authorities.add(new SimpleGrantedAuthority(p)));
                            selected.roles().forEach(r->authorities.add(new SimpleGrantedAuthority("FARM_"+r)));
                        }
                        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt.getToken(),authorities));
                        boolean business=!path(req).startsWith("/api/v1/auth/")
                            && !path(req).startsWith("/api/v1/workspace")
                            && !path(req).matches("/api/v1/(users|roles|permissions)(/.*)?");
                        if(business && (selected==null || !selected.onboardingStatus().equals("READY"))) {
                            api.write(req,res,403,"SETUP_REQUIRED","Chọn trang trại, thêm nhân viên và phân công vị trí trước khi thiết lập."); return;
                        }
                    }
                    chain.doFilter(req,res);
                    if(res.getStatus()>=400) status.setRollbackOnly();
                } catch(IOException|ServletException ex) { throw new FilterFailure(ex); }
            });
        } catch(FilterFailure ex) {
            if(ex.getCause() instanceof IOException io) throw io;
            throw (ServletException)ex.getCause();
        } catch(UnexpectedRollbackException ex) { if(res.getStatus()<400) throw ex; }
    }
    private static class FilterFailure extends RuntimeException { FilterFailure(Exception cause) { super(cause); } }
}
