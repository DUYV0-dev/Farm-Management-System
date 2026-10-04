package com.farmmanagement.backend.config;

import com.farmmanagement.backend.auth.AuthStore;
import com.farmmanagement.backend.common.Api;


import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean public PasswordEncoder passwordEncoder() { return new Argon2PasswordEncoder(16,32,1,19456,2); }
    @Bean public SecretKeySpec jwtKey(@Value("${auth.jwt.secret}") String secret) {
        byte[] decoded;
        try { decoded=Base64.getDecoder().decode(secret); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("JWT_SECRET must be Base64"); }
        if(decoded.length<32) throw new IllegalStateException("JWT_SECRET must contain at least 32 random bytes");
        return new SecretKeySpec(decoded,"HmacSHA256");
    }
    @Bean public JwtEncoder jwtEncoder(SecretKeySpec key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean public JwtDecoder jwtDecoder(SecretKeySpec key,@Value("${auth.jwt.issuer}") String issuer,
                               @Value("${auth.jwt.audience}") String audience) {
        var decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> contract=jwt->{
            try {
                Instant now=Instant.now();
                boolean valid=jwt.getAudience().contains(audience) && jwt.getExpiresAt()!=null && jwt.getIssuedAt()!=null
                    && jwt.getExpiresAt().isAfter(now) && !jwt.getIssuedAt().isAfter(now)
                    && Duration.between(jwt.getIssuedAt(),jwt.getExpiresAt()).getSeconds()==86400
                    && Integer.parseInt(jwt.getSubject())>0
                    && UUID.fromString(jwt.getClaimAsString("sessionId")).toString().equals(jwt.getId());
                if(valid) return OAuth2TokenValidatorResult.success();
            } catch(RuntimeException ignored) { }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ZERO),new JwtIssuerValidator(issuer),contract));
        return decoder;
    }
    @Bean SecurityFilterChain security(HttpSecurity http,AuthStore store,Api api) throws Exception {
        http.csrf(csrf->csrf.disable()) // Only explicit Authorization bearer; no auth cookies, no HTTP Basic.
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c->c.disable())
            .authorizeHttpRequests(a->a
                .requestMatchers(HttpMethod.GET,"/","/index.html","/app.js","/style.css").permitAll()
                .requestMatchers(HttpMethod.GET,"/ui/farm.html","/ui/material.html","/ui/js/**","/ui/css/**").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/auth/me").authenticated()
                .requestMatchers(HttpMethod.POST,"/api/v1/auth/logout").authenticated()
                .requestMatchers(HttpMethod.POST,"/api/v1/auth/password").authenticated()
                .requestMatchers(HttpMethod.GET,"/admin/users").hasAuthority("users:read")
                .requestMatchers(HttpMethod.GET,"/admin/roles").hasAuthority("roles:read")
                .requestMatchers(HttpMethod.GET,"/admin/farms").hasAuthority("farms:read")
                .requestMatchers("/api/v1/users", "/api/v1/users/**", "/api/v1/roles", "/api/v1/roles/**", "/api/v1/permissions").authenticated()
                .requestMatchers("/api/v1/farms", "/api/v1/farms/**", "/api/v1/plots", "/api/v1/plots/**", 
                                 "/api/v1/crops", "/api/v1/crops/**", "/api/v1/seasons", "/api/v1/seasons/**").authenticated()
                .requestMatchers("/api/v1/material-categories", "/api/v1/material-categories/**",
                    "/api/v1/materials", "/api/v1/materials/**", "/api/v1/warehouses", "/api/v1/warehouses/**",
                    "/api/v1/inventory", "/api/v1/inventory/**").authenticated()
                .anyRequest().denyAll())
            .exceptionHandling(e->e
                .authenticationEntryPoint((q,s,x)->api.write(q,s,401,"UNAUTHENTICATED","Vui lòng đăng nhập lại."))
                .accessDeniedHandler((q,s,x)->api.write(q,s,403,"FORBIDDEN","Bạn không có quyền truy cập.")))
            .oauth2ResourceServer(o->o
                .authenticationEntryPoint((q,s,x)->api.write(q,s,401,"UNAUTHENTICATED","Vui lòng đăng nhập lại."))
                .jwt(j->j.jwtAuthenticationConverter(jwt->{
                    int uid=Integer.parseInt(jwt.getSubject());
                    UUID sid=UUID.fromString(jwt.getClaimAsString("sessionId"));
                    if(!store.validSession(sid,uid)) throw new OAuth2AuthenticationException("invalid_token");
                    var roles=store.roles(uid);
                    if(roles.isEmpty()) throw new OAuth2AuthenticationException("invalid_token");
                    var authorities=new ArrayList<SimpleGrantedAuthority>();
                    roles.forEach(r->authorities.add(new SimpleGrantedAuthority("ROLE_"+r)));
                    store.permissions(uid).forEach(p->authorities.add(new SimpleGrantedAuthority(p)));
                    return new JwtAuthenticationToken(jwt,authorities);
                })));
        http.headers(h->h.frameOptions(f->f.sameOrigin()).contentSecurityPolicy(c->c.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'self'; base-uri 'none'; form-action 'self'")));
        return http.build();
    }
}
