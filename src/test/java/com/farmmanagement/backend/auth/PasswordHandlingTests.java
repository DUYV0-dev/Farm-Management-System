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

class PasswordHandlingTests {
    AuthStore store;
    AuthService service;
    String oldPassword="CurrentSecret123", newPassword="DifferentSecret456";
    @BeforeEach void setup() {
        store=mock(AuthStore.class); var config=new SecurityConfig(); var passwords=config.passwordEncoder();
        service=new AuthService(store,passwords,config.jwtEncoder(config.jwtKey(Base64.getEncoder().encodeToString(new byte[32]))),"test","test");
        when(store.byId(1,true)).thenReturn(Optional.of(new AuthStore.User(1,"user",passwords.encode(oldPassword),"User","u@test.vn","ACTIVE",Instant.now())));
    }
    @Test void changingPasswordChecksCurrentPasswordAndHashesNewOne() {
        service.changePassword(1,oldPassword,newPassword);
        var hash=org.mockito.ArgumentCaptor.forClass(String.class); verify(store).changePassword(eq(1),hash.capture());
        assertTrue(new SecurityConfig().passwordEncoder().matches(newPassword,hash.getValue()));
    }
    @Test void wrongCurrentPasswordCannotChangePassword() {
        assertThrows(BadCredentialsException.class,()->service.changePassword(1,"incorrect",newPassword));
        verify(store,never()).changePassword(anyInt(),anyString());
    }
    @Test void reusedAndWeakPasswordsRejected() {
        assertThrows(DomainException.class,()->service.changePassword(1,oldPassword,oldPassword));
        assertThrows(IllegalArgumentException.class,()->service.changePassword(1,oldPassword,"weak"));
        verify(store,never()).changePassword(anyInt(),anyString());
    }
    @Test void publicUserNeverContainsPasswordHash() {
        var db=mock(org.springframework.jdbc.core.JdbcTemplate.class); var realStore=new AuthStore(db);
        when(db.queryForList(anyString(),eq(String.class),eq(1))).thenReturn(List.of());
        var output=realStore.publicUser(new AuthStore.User(1,"user","TOP_SECRET_HASH","User","u@test.vn","ACTIVE",Instant.now()));
        assertFalse(output.toString().contains("TOP_SECRET_HASH")); assertFalse(output.containsKey("hash"));
    }
}
