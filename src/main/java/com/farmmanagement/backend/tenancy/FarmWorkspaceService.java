package com.farmmanagement.backend.tenancy;

import com.farmmanagement.backend.auth.AuthStore;
import com.farmmanagement.backend.common.*;
import com.farmmanagement.backend.management.ManagementStore;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FarmWorkspaceService {
    public static final Set<String> JOBS=Set.of("MANAGER","AGRONOMIST","WAREHOUSE","ACCOUNTANT","WORKER");
    private final FarmWorkspaceStore farms;
    private final ManagementStore accounts;
    private final AuthStore auth;
    private final PasswordEncoder passwords;
    public FarmWorkspaceService(FarmWorkspaceStore farms,ManagementStore accounts,AuthStore auth,PasswordEncoder passwords) {
        this.farms=farms;this.accounts=accounts;this.auth=auth;this.passwords=passwords;
    }
    public Map<String,Object> register(String username,String password,String fullName,String email,String farmName,String address,String areaHa,String requestId) {
        Input.strongPassword(password);
        if (!username.matches("[a-z0-9._-]{3,50}")) throw new IllegalArgumentException();
        BigDecimal area;
        try { area=new BigDecimal(areaHa); } catch(NumberFormatException ex) { throw new IllegalArgumentException(); }
        if(area.signum()<=0 || area.compareTo(new BigDecimal("9999999"))>0 || area.stripTrailingZeros().scale()>2) throw new IllegalArgumentException();
        int uid=accounts.createUser(username,passwords.encode(password),fullName,email);
        accounts.assignRoles(uid,List.of(accounts.defaultRole()));
        int farm=farms.registerFarm(uid,farmName,address,area);
        accounts.audit(uid,"USER",uid,"CREATE",requestId);
        accounts.audit(uid,"FARM",farm,"CREATE",requestId);
        return Map.of("userId",uid,"farmId",farm,"nextStep","LOGIN");
    }
    public FarmWorkspaceStore.Membership selected(int uid,int farm) {
        return farms.memberships(uid).stream().filter(m->m.farmId()==farm && m.status().equals("ACTIVE") && m.farmStatus().equals("ACTIVE"))
            .findFirst().orElseThrow(()->new AccessDeniedException("Farm membership required"));
    }
    private void owner(int uid,int farm) {
        // Lock the farm to serialize staff changes and completion with revocation.
        farms.context(uid,farm);
        accounts.lockAdministration();
        if(!selected(uid,farm).roles().contains("OWNER")) throw new AccessDeniedException("Only the farm owner manages staff");
    }
    public List<Map<String,Object>> staff(int uid,int farm) { owner(uid,farm); return farms.staff(farm); }
    public Map<String,Object> add(int uid,int farm,String username,String password,String name,String email,String job,String requestId) {
        owner(uid,farm); Input.strongPassword(password);
        if(!JOBS.contains(job) || !username.matches("[a-z0-9._-]{3,50}")) throw new IllegalArgumentException();
        int target=accounts.createUser(username,passwords.encode(password),name,email);
        accounts.assignRoles(target,List.of(accounts.defaultRole())); farms.member(farm,target,job);
        accounts.audit(uid,"FARM_MEMBER",target,"CREATE",requestId);
        return farms.staff(farm).stream().filter(m->m.get("id").equals(target)).findFirst().orElseThrow();
    }
    public void change(int uid,int farm,int target,String job,String status,String requestId) {
        owner(uid,farm);
        if(!JOBS.contains(job) || !Set.of("ACTIVE","INACTIVE").contains(status)) throw new IllegalArgumentException();
        var member=farms.staff(farm).stream().filter(m->m.get("id").equals(target)).findFirst().orElseThrow(DomainException::missing);
        if(((List<?>)member.get("roles")).contains("OWNER")) throw DomainException.conflict("Không thể sửa chủ trang trại từ chức năng nhân viên.");
        farms.role(farm,target,job); farms.status(farm,target,status);
        accounts.audit(uid,"FARM_MEMBER",target,"ROLE_CHANGE",requestId);
    }
    public void complete(int uid,int farm,String requestId) {
        owner(uid,farm);
        if(farms.staff(farm).stream().noneMatch(m->!((List<?>)m.get("roles")).contains("OWNER") && m.get("status").equals("ACTIVE")))
            throw DomainException.conflict("Thêm ít nhất một nhân viên và phân công vị trí trước khi thiết lập trang trại.");
        farms.complete(farm); accounts.audit(uid,"FARM",farm,"UPDATE",requestId);
    }
}
