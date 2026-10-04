package com.farmmanagement.backend.management;

import com.farmmanagement.backend.common.DomainException;
import com.farmmanagement.backend.auth.AuthStore;
import com.farmmanagement.backend.common.Input;


import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ManagementService {
    private final AuthStore users;
    private final ManagementStore store;
    private final PasswordEncoder passwords;
    public ManagementService(AuthStore users, ManagementStore store, PasswordEncoder passwords) {
        this.users=users; this.store=store; this.passwords=passwords;
    }
    private void lockAdministration(int actor, String permission) {
        store.lockAdministration();
        // Authentication and @PreAuthorize may have run before another admin's transaction.
        // Re-read the actor after acquiring the shared lock, before any administrative write.
        var current = users.byId(actor);
        if (current.isEmpty() || !current.get().status().equals("ACTIVE")
                || users.roles(actor).isEmpty() || !users.permissions(actor).contains(permission))
            throw new AccessDeniedException("Administrative permission is no longer available");
    }
    private AuthStore.User user(int id) { return users.byId(id).orElseThrow(DomainException::missing); }
    private ManagementStore.Role role(int id) { return store.role(id).orElseThrow(DomainException::missing); }
    private boolean admin(int id) { return users.roles(id).contains("SYSTEM_ADMIN"); }
    private void canGrant(int actor, ManagementStore.Role role) {
        if (admin(actor)) return;
        if (role.name().equals("SYSTEM_ADMIN") || !users.permissions(actor).containsAll(role.permissions()))
            throw new AccessDeniedException("Cannot grant permissions you do not have");
    }
    private AuthStore.User editable(int actor, int target) {
        AuthStore.User user=user(target);
        if (!admin(actor) && (admin(target) || !users.permissions(actor).containsAll(users.permissions(target))))
            throw new AccessDeniedException("Cannot manage a more privileged account");
        return user;
    }
    private void retainAdmin(AuthStore.User target) {
        if (target.status().equals("ACTIVE") && admin(target.id()) && store.activeAdmins() <= 1)
            throw DomainException.conflict("Phải giữ ít nhất một quản trị viên đang hoạt động.");
    }
    @PreAuthorize("hasAuthority('users:read')")
    @Transactional(readOnly=true)
    public Map<String,Object> list(int page, int size, String search) {
        if (page < 0 || page > 100000 || size < 1 || size > 100 || search.length() > 100) throw new IllegalArgumentException();
        return Map.of("items",store.userIds(page*size,size,search).stream().map(id->users.publicUser(user(id))).toList(),
            "page",page,"size",size,"total",store.userCount(search));
    }
    @PreAuthorize("hasAuthority('users:read')")
    @Transactional(readOnly=true)
    public Map<String,Object> get(int id) { return users.publicUser(user(id)); }
    @PreAuthorize("hasAuthority('users:create')")
    public Map<String,Object> create(int actor, String username, String password, String fullName, String email, String requestId) {
        Input.strongPassword(password);
        lockAdministration(actor,"users:create");
        int defaultRole=store.defaultRole();
        canGrant(actor,role(defaultRole));
        int id=store.createUser(username,passwords.encode(password),fullName,email);
        store.assignRoles(id,List.of(defaultRole));
        store.audit(actor,"USER",id,"CREATE",requestId);
        store.audit(actor,"USER_ROLE",id,"ROLE_CHANGE",requestId);
        return users.publicUser(user(id));
    }
    @PreAuthorize("hasAuthority('users:update')")
    public Map<String,Object> update(int actor, int id, String fullName, String email, String requestId) {
        lockAdministration(actor,"users:update"); editable(actor,id);
        store.updateUser(id,fullName,email); store.audit(actor,"USER",id,"UPDATE",requestId);
        return users.publicUser(user(id));
    }
    @PreAuthorize("hasAuthority('users:status')")
    public Map<String,Object> status(int actor, int id, String status, String requestId) {
        if (!Set.of("ACTIVE","INACTIVE").contains(status)) throw new IllegalArgumentException();
        lockAdministration(actor,"users:status"); var target=editable(actor,id);
        if (status.equals("INACTIVE")) {
            if (actor==id) throw DomainException.conflict("Không thể tự vô hiệu hóa tài khoản.");
            retainAdmin(target);
        }
        store.status(id,status);
        if (status.equals("INACTIVE")) users.revokeAll(id);
        store.audit(actor,"USER",id,"UPDATE",requestId);
        return users.publicUser(user(id));
    }
    @PreAuthorize("hasAuthority('users:assign')")
    public Map<String,Object> assign(int actor, int id, List<Integer> roles, String requestId) {
        if (roles.isEmpty() || roles.size()>50 || new HashSet<>(roles).size()!=roles.size()) throw new IllegalArgumentException();
        lockAdministration(actor,"users:assign"); var target=editable(actor,id);
        var selected=roles.stream().map(this::role).toList();
        selected.forEach(r->canGrant(actor,r));
        if (selected.stream().noneMatch(r->r.name().equals("SYSTEM_ADMIN"))) retainAdmin(target);
        store.assignRoles(id,roles); store.audit(actor,"USER_ROLE",id,"ROLE_CHANGE",requestId);
        return users.publicUser(user(id));
    }
    @PreAuthorize("hasAuthority('users:password')")
    public void resetPassword(int actor, int id, String password, String requestId) {
        Input.strongPassword(password);
        if (actor==id) throw DomainException.conflict("Dùng chức năng đổi mật khẩu với mật khẩu hiện tại.");
        lockAdministration(actor,"users:password"); editable(actor,id);
        users.changePassword(id,passwords.encode(password)); store.audit(actor,"USER",id,"UPDATE",requestId);
    }
    @PreAuthorize("hasAuthority('roles:read')")
    @Transactional(readOnly=true)
    public List<ManagementStore.Role> roles() { return store.roles(); }
    @PreAuthorize("hasAuthority('roles:read')")
    @Transactional(readOnly=true)
    public ManagementStore.Role getRole(int id) { return role(id); }
    @PreAuthorize("hasAuthority('roles:read')")
    @Transactional(readOnly=true)
    public List<Map<String,Object>> permissions() { return store.permissions(); }
    @PreAuthorize("hasAuthority('roles:write')")
    public ManagementStore.Role createRole(int actor, String name, String description, String requestId) {
        if (Set.of("SYSTEM_ADMIN","USER").contains(name)) throw DomainException.conflict("Vai trò hệ thống đã được dành riêng.");
        lockAdministration(actor,"roles:write"); int id=store.createRole(name,description);
        store.audit(actor,"ROLE",id,"CREATE",requestId); return role(id);
    }
    @PreAuthorize("hasAuthority('roles:write')")
    public ManagementStore.Role updateRole(int actor, int id, String name, String description, String requestId) {
        lockAdministration(actor,"roles:write"); var existing=role(id); canGrant(actor,existing);
        if (Set.of("SYSTEM_ADMIN","USER").contains(existing.name()) || Set.of("SYSTEM_ADMIN","USER").contains(name))
            throw DomainException.conflict("Không thể sửa tên vai trò hệ thống mặc định.");
        store.updateRole(id,name,description); store.audit(actor,"ROLE",id,"UPDATE",requestId); return role(id);
    }
    @PreAuthorize("hasAuthority('roles:permissions')")
    public ManagementStore.Role permissions(int actor, int id, List<String> permissions, String requestId) {
        lockAdministration(actor,"roles:permissions"); var existing=role(id); canGrant(actor,existing);
        if (existing.name().equals("SYSTEM_ADMIN")) throw DomainException.conflict("Quyền của SYSTEM_ADMIN được bảo vệ.");
        if (!store.permissionCodes().containsAll(permissions)) throw new IllegalArgumentException();
        if (!admin(actor) && !users.permissions(actor).containsAll(permissions)) throw new AccessDeniedException("Cannot grant permissions you do not have");
        store.setPermissions(id,permissions); store.audit(actor,"ROLE",id,"ROLE_CHANGE",requestId); return role(id);
    }
}
