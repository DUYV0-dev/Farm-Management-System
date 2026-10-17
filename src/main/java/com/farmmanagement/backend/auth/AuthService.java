package com.farmmanagement.backend.auth;

import com.farmmanagement.backend.common.DomainException;
import com.farmmanagement.backend.common.Input;


import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AuthStore store;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final String issuer, audience, dummyHash;
    public AuthService(AuthStore store,PasswordEncoder passwords,JwtEncoder encoder,
            @Value("${auth.jwt.issuer}") String issuer,@Value("${auth.jwt.audience}") String audience) {
        this.store=store; this.passwords=passwords; this.encoder=encoder;this.issuer=issuer;this.audience=audience;
        this.dummyHash=passwords.encode(UUID.randomUUID().toString());
    }
    @Transactional
    public Map<String,Object> login(String username,String password) {
        var optional=store.byName(username,true);
        var hash=optional.map(AuthStore.User::hash).orElse(dummyHash);
        boolean matches;
        try { matches=passwords.matches(password,hash); }
        catch (IllegalArgumentException ex) { matches=false; }
        if(optional.isEmpty() || !matches || !optional.get().status().equals("ACTIVE")) throw new BadCredentialsException("Invalid credentials");
        var user=optional.get();
        if(store.roles(user.id()).isEmpty()) throw new BadCredentialsException("Invalid credentials");
        Instant now=Instant.now().truncatedTo(ChronoUnit.SECONDS), end=now.plusSeconds(86400);
        UUID sid=UUID.randomUUID(); store.createSession(sid,user.id(),now,end);
        var claims=JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience))
            .subject(Integer.toString(user.id())).id(sid.toString()).claim("sessionId",sid.toString())
            .issuedAt(now).expiresAt(end).build();
        String token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        return Map.of("accessToken",token,"tokenType","Bearer","expiresIn",86400,
            "expiresAt",end.toString(),"sessionId",sid.toString(),"user",store.publicUser(user));
    }
    public Map<String,Object> me(int uid) {
        var user=store.byId(uid).orElseThrow(()->new BadCredentialsException("Invalid user"));
        return Map.of("user",store.publicUser(user),"memberships",store.memberships(uid));
    }
    @Transactional
    public void logout(Jwt jwt) { store.revoke(UUID.fromString(jwt.getClaimAsString("sessionId")),Integer.parseInt(jwt.getSubject())); }

    @Transactional
    public void changePassword(int uid, String currentPassword, String newPassword) {
        Input.strongPassword(newPassword);
        var user=store.byId(uid,true).orElseThrow(()->new BadCredentialsException("Invalid user"));
        if (!user.status().equals("ACTIVE") || !passwords.matches(currentPassword,user.hash()))
            throw new BadCredentialsException("Invalid credentials");
        if (passwords.matches(newPassword,user.hash()))
            throw DomainException.conflict("Mật khẩu mới phải khác mật khẩu hiện tại.");
        store.changePassword(uid,passwords.encode(newPassword));
    }
}
