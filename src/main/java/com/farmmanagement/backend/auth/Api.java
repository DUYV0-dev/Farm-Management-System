package com.farmmanagement.backend.auth;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class Api {
    private final ObjectMapper mapper;
    public Api(ObjectMapper mapper) { this.mapper = mapper; }
    public static String requestId(HttpServletRequest req) {
        return (String) req.getAttribute("requestId");
    }
    public static Map<String,Object> success(Object data, HttpServletRequest req) {
        return Map.of("data",data,"requestId",requestId(req));
    }
    public static Map<String,Object> error(HttpServletRequest req, String code, String message) {
        return Map.of("requestId",requestId(req),"timestamp",Instant.now().toString(),
            "path",req.getRequestURI(),"error",Map.of("code",code,"message",message,"details",List.of()));
    }
    public void write(HttpServletRequest req, HttpServletResponse res, int status, String code, String msg) throws IOException {
        res.setStatus(status); res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(mapper.writeValueAsString(error(req,code,msg)));
    }
}
