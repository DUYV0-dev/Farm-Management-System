package com.farmmanagement.backend.auth;

import com.farmmanagement.backend.common.*;
import com.farmmanagement.backend.config.SecurityConfig;
import com.farmmanagement.backend.management.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

class AuthSecurityTests {
    SecurityConfig config = new SecurityConfig();
    javax.crypto.spec.SecretKeySpec key;
    JwtEncoder encoder;
    JwtDecoder decoder;
    @BeforeEach void setup() {
        byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);
        key=config.jwtKey(Base64.getEncoder().encodeToString(bytes));
        encoder=config.jwtEncoder(key);decoder=config.jwtDecoder(key,"test-issuer","test-audience");
    }
    String token(String issuer,String audience,Instant issued,Instant expires,String subject,String sid,String id) {
        var claims=JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject(subject)
            .issuedAt(issued).expiresAt(expires).claim("sessionId",sid).id(id).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }
    @Test void passwordUsesArgon2idAndUniqueSalt() {
        var pw=config.passwordEncoder();String secret=UUID.randomUUID()+"Ab9";
        String a=pw.encode(secret),b=pw.encode(secret);
        assertTrue(a.startsWith("$argon2id$"));assertNotEquals(a,b);
        assertTrue(pw.matches(secret,a));assertFalse(pw.matches("incorrect",a));
    }
    @Test void weakSigningKeyRejected() { assertThrows(IllegalStateException.class,()->config.jwtKey("YWJj")); }
    @Test void valid24HourTokenAccepted() {
        var now=Instant.now().minusSeconds(1);String sid=UUID.randomUUID().toString();
        var jwt=decoder.decode(token("test-issuer","test-audience",now,now.plusSeconds(86400),"1",sid,sid));
        assertEquals("1",jwt.getSubject());
    }
    @Test void expiredTokenRejected() {
        var now=Instant.now().minusSeconds(86402);String sid=UUID.randomUUID().toString();
        assertThrows(JwtException.class,()->decoder.decode(token("test-issuer","test-audience",now,now.plusSeconds(86400),"1",sid,sid)));
    }
    @Test void wrongIssuerRejected() {
        var now=Instant.now().minusSeconds(1);String sid=UUID.randomUUID().toString();
        assertThrows(JwtException.class,()->decoder.decode(token("other","test-audience",now,now.plusSeconds(86400),"1",sid,sid)));
    }
    @Test void wrongAudienceRejected() {
        var now=Instant.now().minusSeconds(1);String sid=UUID.randomUUID().toString();
        assertThrows(JwtException.class,()->decoder.decode(token("test-issuer","other",now,now.plusSeconds(86400),"1",sid,sid)));
    }
    @Test void inconsistentSessionRejected() {
        var now=Instant.now().minusSeconds(1);String sid=UUID.randomUUID().toString();
        assertThrows(JwtException.class,()->decoder.decode(token("test-issuer","test-audience",now,now.plusSeconds(86400),"1",sid,"other")));
    }
    @Test void wrongSignatureRejected() {
        var now=Instant.now().minusSeconds(1);String sid=UUID.randomUUID().toString();
        String value=token("test-issuer","test-audience",now,now.plusSeconds(86400),"1",sid,sid);
        byte[] other=new byte[32];new java.security.SecureRandom().nextBytes(other);
        var otherDecoder=config.jwtDecoder(config.jwtKey(Base64.getEncoder().encodeToString(other)),"test-issuer","test-audience");
        assertThrows(JwtException.class,()->otherDecoder.decode(value));
    }
    @Test void inactiveUserCannotLoginEvenWithCorrectPassword() {
        var store=mock(AuthStore.class);var passwords=config.passwordEncoder();String secret=UUID.randomUUID()+"Ab9";
        when(store.byName("locked",true)).thenReturn(Optional.of(new AuthStore.User(1,"locked",passwords.encode(secret),"Locked","a@example.test","INACTIVE",Instant.now())));
        var service=new AuthService(store,passwords,encoder,"test-issuer","test-audience");
        assertThrows(BadCredentialsException.class,()->service.login("locked",secret));
        verify(store,never()).createSession(any(),anyInt(),any(),any());
    }
    @Test void noSystemRoleCannotLogin() {
        var store=mock(AuthStore.class);var passwords=config.passwordEncoder();String secret=UUID.randomUUID()+"Ab9";
        when(store.byName("norole",true)).thenReturn(Optional.of(new AuthStore.User(1,"norole",passwords.encode(secret),"No Role","a@example.test","ACTIVE",Instant.now())));
        when(store.roles(1)).thenReturn(List.of());
        var service=new AuthService(store,passwords,encoder,"test-issuer","test-audience");
        assertThrows(BadCredentialsException.class,()->service.login("norole",secret));
        verify(store,never()).createSession(any(),anyInt(),any(),any());
    }
}
