package com.farmmanagement.backend.portal;

import com.farmmanagement.backend.common.*;
import com.farmmanagement.backend.tenancy.FarmWorkspaceStore;
import com.farmmanagement.backend.management.ManagementStore;
import com.farmmanagement.backend.sales.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountPortalService {
    private final AccountPortalStore store;
    private final FarmWorkspaceStore farms;
    private final ManagementStore audit;
    private final CustomerService customers;
    private final SalesOrderService sales;
    public AccountPortalService(AccountPortalStore store,FarmWorkspaceStore farms,ManagementStore audit,CustomerService customers,SalesOrderService sales) {
        this.store=store;this.farms=farms;this.audit=audit;this.customers=customers;this.sales=sales;
    }
    private List<FarmWorkspaceStore.Membership> owned(int uid) {
        return farms.memberships(uid).stream().filter(m->m.status().equals("ACTIVE") && m.farmStatus().equals("ACTIVE") && m.roles().contains("OWNER")).toList();
    }
    private <T> T owner(int uid,int farm,Supplier<T> action) {
        var member=owned(uid).stream().filter(m->m.farmId()==farm).findFirst().orElseThrow(()->new AccessDeniedException("Owner required"));
        var original=SecurityContextHolder.getContext().getAuthentication();
        var jwt=(JwtAuthenticationToken)original;
        var authorities=new ArrayList<org.springframework.security.core.GrantedAuthority>(original.getAuthorities());
        member.permissions().forEach(p->authorities.add(new SimpleGrantedAuthority(p)));
        authorities.add(new SimpleGrantedAuthority("FARM_OWNER"));
        farms.context(uid,farm);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt.getToken(),authorities));
        try {
            T result=action.get();
            farms.context(uid,null);
            return result;
        } finally {
            // On SQL failure the transaction is aborted. Its local tenant settings
            // are cleared by rollback; issuing more SQL here would mask the cause.
            SecurityContextHolder.getContext().setAuthentication(original);
        }
    }
    public Map<String,Object> profile(int uid) {
        return store.profile(uid);
    }
    public Map<String,Object> profile(int uid,String name,String email,String phone,String bio,String request) {
        if(!phone.isEmpty() && !phone.matches("[+0-9 ()-]{7,20}")) throw new IllegalArgumentException();
        store.updateProfile(name,email,phone,bio,uid);
        audit.audit(uid,"USER",uid,"UPDATE",request);return profile(uid);
    }
    public Map<String,Object> createFarm(int uid,String name,String address,BigDecimal area,String request) {
        if(area.signum()<=0 || area.compareTo(new BigDecimal("9999999"))>0 || area.stripTrailingZeros().scale()>2) throw new IllegalArgumentException();
        int id=farms.registerFarm(uid,name,address,area);farms.context(uid,id);farms.complete(id);
        audit.audit(uid,"FARM",id,"CREATE",request);farms.context(uid,null);return Map.of("id",id);
    }
    public Map<String,Object> overview(int uid,LocalDate from,LocalDate to) {
        if(from.isAfter(to) || from.plusYears(5).isBefore(to)) throw new IllegalArgumentException();
        var farmList=new ArrayList<Map<String,Object>>();var people=new ArrayList<Map<String,Object>>();
        var orders=new ArrayList<Map<String,Object>>();var listings=new ArrayList<Map<String,Object>>();
        var monthly=new TreeMap<String,BigDecimal>();
        BigDecimal revenue=BigDecimal.ZERO,expense=BigDecimal.ZERO;
        for(var m:owned(uid)) {
            var summary=owner(uid,m.farmId(),()->{
                var row=new LinkedHashMap<String,Object>(store.farm(m.farmId()));
                row.put("revenue",store.revenue(m.farmId(),from,to));
                row.put("expense",store.expenses(m.farmId(),from,to));
                row.put("plots",store.plotCount(m.farmId()));
                row.put("pending",store.pendingOrders(m.farmId()));
                for(var person:farms.staff(m.farmId())) if(!((List<?>)person.get("roles")).contains("OWNER")) {
                    var p=new LinkedHashMap<>(person);p.put("farmId",m.farmId());p.put("farmName",m.farmName());people.add(p);
                }
                // Limit merchandise/order detail to bound the account overview response.
                orders.addAll(store.orders(m.farmName(),m.farmId()));
                listings.addAll(store.listings(m.farmName(),m.farmId()));
                store.monthly(m.farmId(),from,to).forEach((month,amount)->monthly.merge(month,amount,BigDecimal::add));
                return row;
            });
            farmList.add(summary);revenue=revenue.add((BigDecimal)summary.get("revenue"));expense=expense.add((BigDecimal)summary.get("expense"));
        }
        return Map.of("profile",profile(uid),"farms",farmList,"people",people,"orders",orders,"listings",listings,
            "revenue",revenue,"expense",expense,"monthly",monthly,"from",from,"to",to);
    }
    public Map<String,Object> publish(int uid,int farm,int product,String name,String description,BigDecimal price,boolean published,int version,String request) {
        if(price.signum()<=0 || price.compareTo(new BigDecimal("999999999.99"))>0 || price.stripTrailingZeros().scale()>2 || version<0) throw new IllegalArgumentException();
        return owner(uid,farm,()->{
            var products=store.lockProduct(product,farm);
            if(products.isEmpty()) throw DomainException.missing();
            if(version==0) {
                store.createListing(farm,product,name,description,price,published);
            } else if(store.updateListing(name,description,price,published,product,farm,version)!=1) {
                throw DomainException.conflict("Sản phẩm đã thay đổi. Hãy tải lại trang.");
            }
            audit.audit(uid,"PRODUCT",product,"UPDATE",request);
            return store.listing(product);
        });
    }
    public Map<String,Object> order(int uid,int farm,UUID id) {
        return owner(uid,farm,()->{
            var result=new LinkedHashMap<>(orderRow(farm,id,false));
            result.put("items",store.orderItems(id));return result;
        });
    }
    private Map<String,Object> orderRow(int farm,UUID id,boolean lock) {
        var rows=store.orderRows(farm,id,lock);
        if(rows.isEmpty()) throw DomainException.missing();return rows.getFirst();
    }
    public Map<String,Object> decide(int uid,int farm,UUID id,boolean accept,String request) {
        return owner(uid,farm,()->{
            var row=orderRow(farm,id,true);String status=(String)row.get("status");
            if(!status.equals("PENDING")) {
                if(!status.equals(accept?"ACCEPTED":"REJECTED")) throw DomainException.conflict("Đơn đã được xử lý.");
                return Map.of("status",status,"salesOrderId",Optional.ofNullable(row.get("sales_order_id")).orElse(0));
            }
            Integer so=null;
            if(accept) {
                // Guest names are not identities. Keep each order's contact snapshot
                // distinct, including two unrelated buyers sharing the same name.
                int customer=customers.createCustomer(uid,row.get("buyer_name")+" · "+id,(String)row.get("buyer_name"),(String)row.get("email"),(String)row.get("phone"),(String)row.get("address"),"INDIVIDUAL",null,"Cửa hàng trực tuyến",request).id();
                var items=store.salesItems(id);
                so=sales.createSalesOrder(uid,null,customer,farm,LocalDate.now(),null,"CONFIRMED","UNPAID",(String)row.get("address"),(String)row.get("note"),items,request).id();
            }
            store.decideOrder(accept?"ACCEPTED":"REJECTED",so,id);
            audit.audit(uid,"FARM",farm,"UPDATE",request);
            return Map.of("status",accept?"ACCEPTED":"REJECTED","salesOrderId",so==null?0:so);
        });
    }
}
