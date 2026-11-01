package com.farmmanagement.backend.portal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.farmmanagement.backend.common.DomainException;
import com.farmmanagement.backend.sales.SalesOrderService;

@Repository
public class AccountPortalStore {
    private final JdbcTemplate db;
    public AccountPortalStore(JdbcTemplate db) { this.db=db; }

    private Map<String,Object> row(String sql,Object... args) {
        return db.queryForList(sql,args).stream().findFirst().orElseThrow(DomainException::missing);
    }
    public Map<String,Object> profile(int uid) {
        return db.queryForMap("SELECT username,full_name AS \"fullName\",email,phone,bio FROM public.app_user WHERE user_id=?",uid);
    }

    public int updateProfile(String name,String email,String phone,String bio,int uid) {
        return db.update("UPDATE public.app_user SET full_name=?,email=?,phone=?,bio=? WHERE user_id=?",name,email,phone,bio,uid);
    }

    public Map<String,Object> farm(int farm) {
        return db.queryForMap("SELECT farm_id AS id,farm_name AS name,address,area_ha AS area FROM public.farm WHERE farm_id=?",farm);
    }

    public BigDecimal revenue(int farm,LocalDate from,LocalDate to) {
        return db.queryForObject("SELECT COALESCE(sum(total_amount),0) FROM public.sales_order WHERE farm_id=? AND status='DELIVERED' AND order_date BETWEEN ? AND ?",BigDecimal.class,farm,from,to);
    }

    public BigDecimal expenses(int farm,LocalDate from,LocalDate to) {
        return db.queryForObject("SELECT COALESCE(sum(amount),0) FROM public.farm_expense WHERE farm_id=? AND expense_date BETWEEN ? AND ?",BigDecimal.class,farm,from,to);
    }

    public Long plotCount(int farm) {
        return db.queryForObject("SELECT count(*) FROM public.plot WHERE farm_id=?",Long.class,farm);
    }

    public Long pendingOrders(int farm) {
        return db.queryForObject("SELECT count(*) FROM public.store_order WHERE farm_id=? AND status='PENDING'",Long.class,farm);
    }

    public List<Map<String,Object>> orders(String farmName,int farm) {
        return db.queryForList("SELECT order_id AS id,farm_id AS \"farmId\",?::text AS \"farmName\",buyer_name AS \"buyerName\",phone,email,address,note,total_amount AS total,status,sales_order_id AS \"salesOrderId\",created_at::text AS \"createdAt\" FROM public.store_order WHERE farm_id=? ORDER BY created_at DESC LIMIT 100",farmName,farm);
    }

    public List<Map<String,Object>> listings(String farmName,int farm) {
        return db.queryForList("SELECT p.product_id AS \"productId\",p.product_name AS \"productName\",p.quantity,c.unit,l.listing_id AS id,COALESCE(l.public_name,p.product_name) AS name,COALESCE(l.description,'') AS description,COALESCE(l.price,0) AS price,COALESCE(l.published,false) AS published,COALESCE(l.version,0) AS version,c.farm_id AS \"farmId\",?::text AS \"farmName\" FROM public.harvested_product p JOIN public.crop_production c USING(production_id) LEFT JOIN public.product_listing l USING(product_id) WHERE c.farm_id=? ORDER BY p.product_id DESC LIMIT 200",farmName,farm);
    }

    public Map<String,BigDecimal> monthly(int farm,LocalDate from,LocalDate to) {
        var result=new TreeMap<String,BigDecimal>();
        db.query("SELECT to_char(order_date,'YYYY-MM') AS month,sum(total_amount) AS amount FROM public.sales_order WHERE farm_id=? AND status='DELIVERED' AND order_date BETWEEN ? AND ? GROUP BY 1",rs -> { result.put(rs.getString(1),rs.getBigDecimal(2)); },farm,from,to);
        return result;
    }

    public List<Map<String,Object>> lockProduct(int product,int farm) {
        return db.queryForList("SELECT p.product_id FROM public.harvested_product p JOIN public.crop_production c USING(production_id) WHERE p.product_id=? AND c.farm_id=? FOR UPDATE OF p",product,farm);
    }

    public int createListing(int farm,int product,String name,String description,BigDecimal price,boolean published) {
        return db.update("INSERT INTO public.product_listing(farm_id,product_id,public_name,description,price,published) VALUES (?,?,?,?,?,?)",farm,product,name,description,price,published);
    }

    public int updateListing(String name,String description,BigDecimal price,boolean published,int product,int farm,int version) {
        return db.update("UPDATE public.product_listing SET public_name=?,description=?,price=?,published=?,version=version+1,updated_at=now() WHERE product_id=? AND farm_id=? AND version=?",name,description,price,published,product,farm,version);
    }

    public Map<String,Object> listing(int product) {
        return db.queryForMap("SELECT listing_id AS id,version FROM public.product_listing WHERE product_id=?",product);
    }

    public List<Map<String,Object>> orderItems(UUID id) {
        return db.queryForList("SELECT product_name AS name,quantity,unit,unit_price AS price,total_price AS total FROM public.store_order_item WHERE order_id=? ORDER BY item_id",id);
    }

    public List<Map<String,Object>> orderRows(int farm,UUID id,boolean lock) {
        return db.queryForList("SELECT order_id,farm_id,buyer_name,phone,email,address,note,total_amount,status,sales_order_id,created_at::text AS created_at FROM public.store_order WHERE farm_id=? AND order_id=?"+(lock?" FOR UPDATE":""),farm,id);
    }

    public List<SalesOrderService.ItemInput> salesItems(UUID id) {
        return db.query("SELECT product_id,quantity,unit_price FROM public.store_order_item WHERE order_id=? ORDER BY product_id",(r,n)->new SalesOrderService.ItemInput(r.getInt(1),r.getBigDecimal(2),r.getBigDecimal(3),null),id);
    }

    public int decideOrder(String status,Integer salesOrder,UUID id) {
        return db.update("UPDATE public.store_order SET status=?,sales_order_id=? WHERE order_id=?",status,salesOrder,id);
    }
}
