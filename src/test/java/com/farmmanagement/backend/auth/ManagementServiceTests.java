package com.farmmanagement.backend.auth;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

class ManagementServiceTests {
    AuthStore users;
    ManagementStore store;
    PasswordEncoder passwords;
    ManagementService service;
    final String rid=UUID.randomUUID().toString();
    AuthStore.User user(int id) { return new AuthStore.User(id,"user"+id,"hash","User","u"+id+"@test.vn","ACTIVE",Instant.now()); }
    ManagementStore.Role role(int id,String name,String... permissions) { return new ManagementStore.Role(id,name,"Description",List.of(permissions)); }
    @BeforeEach void setup() {
        users=mock(AuthStore.class); store=mock(ManagementStore.class); passwords=mock(PasswordEncoder.class);
        service=new ManagementService(users,store,passwords);
        when(users.byId(1)).thenReturn(Optional.of(user(1)));
        when(users.byId(2)).thenReturn(Optional.of(user(2)));
        when(users.roles(1)).thenReturn(List.of("SYSTEM_ADMIN"));
        when(users.roles(2)).thenReturn(List.of("USER"));
        when(users.publicUser(any())).thenReturn(Map.of("id",2));
    }
    @Test void cannotDeactivateSelf() {
        assertEquals(409,assertThrows(DomainException.class,()->service.status(1,1,"INACTIVE",rid)).status());
        verify(store,never()).status(anyInt(),anyString());
    }
    @Test void cannotDeactivateLastAdministrator() {
        when(users.roles(2)).thenReturn(List.of("SYSTEM_ADMIN")); when(store.activeAdmins()).thenReturn(1L);
        assertThrows(DomainException.class,()->service.status(1,2,"INACTIVE",rid));
        verify(store,never()).status(anyInt(),anyString());
    }
    @Test void cannotRemoveLastAdministratorRole() {
        when(store.role(3)).thenReturn(Optional.of(role(3,"USER"))); when(store.activeAdmins()).thenReturn(1L);
        assertThrows(DomainException.class,()->service.assign(1,1,List.of(3),rid));
        verify(store,never()).assignRoles(anyInt(),anyList());
    }
    @Test void deactivationRevokesSessionsAndRecordsAudit() {
        service.status(1,2,"INACTIVE",rid);
        verify(store).lockAdministration(); verify(users).revokeAll(2); verify(store).audit(1,"USER",2,"UPDATE",rid);
    }
    @Test void activationDoesNotRestoreSessions() {
        service.status(1,2,"ACTIVE",rid); verify(users,never()).revokeAll(anyInt()); verify(store).status(2,"ACTIVE");
    }
    @Test void delegatedManagerCannotAssignAdmin() {
        when(users.roles(1)).thenReturn(List.of("MANAGER"));
        when(store.role(3)).thenReturn(Optional.of(role(3,"SYSTEM_ADMIN")));
        assertThrows(AccessDeniedException.class,()->service.assign(1,2,List.of(3),rid));
        verify(store,never()).assignRoles(anyInt(),anyList());
    }
    @Test void delegatedManagerCannotGrantHigherPermissions() {
        when(users.roles(1)).thenReturn(List.of("MANAGER"));
        when(users.permissions(1)).thenReturn(List.of("users:assign"));
        when(store.role(3)).thenReturn(Optional.of(role(3,"POWER_USER","users:password")));
        assertThrows(AccessDeniedException.class,()->service.assign(1,2,List.of(3),rid));
    }
    @Test void delegatedManagerCannotResetAdministratorPassword() {
        when(users.roles(1)).thenReturn(List.of("MANAGER")); when(users.roles(2)).thenReturn(List.of("SYSTEM_ADMIN"));
        assertThrows(AccessDeniedException.class,()->service.resetPassword(1,2,"DifferentSecret123",rid));
        verify(users,never()).changePassword(anyInt(),anyString());
    }
    @Test void cannotChangeAdminPermissions() {
        when(store.role(3)).thenReturn(Optional.of(role(3,"SYSTEM_ADMIN")));
        assertThrows(DomainException.class,()->service.permissions(1,3,List.of(),rid));
        verify(store,never()).setPermissions(anyInt(),anyList());
    }
    @Test void rejectsUnknownPermissions() {
        when(store.role(3)).thenReturn(Optional.of(role(3,"MANAGER")));
        when(store.permissionCodes()).thenReturn(List.of("users:read"));
        assertThrows(IllegalArgumentException.class,()->service.permissions(1,3,List.of("unknown:permission"),rid));
    }
    @Test void cannotElevateOwnRolePermissions() {
        when(users.roles(1)).thenReturn(List.of("MANAGER"));
        when(users.permissions(1)).thenReturn(List.of("roles:permissions"));
        when(store.role(3)).thenReturn(Optional.of(role(3,"MANAGER","roles:permissions")));
        when(store.permissionCodes()).thenReturn(List.of("roles:permissions","users:password"));
        assertThrows(AccessDeniedException.class,()->service.permissions(1,3,List.of("users:password"),rid));
    }
    @Test void createHashesPasswordAndAssignsDefaultRoleOnly() {
        when(store.defaultRole()).thenReturn(3); when(store.role(3)).thenReturn(Optional.of(role(3,"USER")));
        when(passwords.encode("ValidPassword123")).thenReturn("encoded");
        when(store.createUser("user2","encoded","User","u2@test.vn")).thenReturn(2);
        service.create(1,"user2","ValidPassword123","User","u2@test.vn",rid);
        verify(store).assignRoles(2,List.of(3)); verify(store).createUser("user2","encoded","User","u2@test.vn");
    }
    @Test void resetHashesPasswordAndUsesRevokingStoreOperation() {
        when(passwords.encode("DifferentSecret123")).thenReturn("encoded");
        service.resetPassword(1,2,"DifferentSecret123",rid); verify(users).changePassword(2,"encoded");
    }
    @Test void emptyRolesAndUnboundedPaginationRejected() {
        assertThrows(IllegalArgumentException.class,()->service.assign(1,2,List.of(),rid));
        assertThrows(IllegalArgumentException.class,()->service.list(-1,20,""));
        assertThrows(IllegalArgumentException.class,()->service.list(0,101,""));
    }
    @Test void weakPasswordDoesNotWrite() {
        assertThrows(IllegalArgumentException.class,()->service.resetPassword(1,2,"weak",rid));
        verify(users,never()).changePassword(anyInt(),anyString());
    }
}
