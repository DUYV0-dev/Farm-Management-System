package com.farmmanagement.backend.sales;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerStore {
    private final JdbcTemplate db;

    public CustomerStore(JdbcTemplate db) {
        this.db = db;
    }

    public record Customer(
        int id,
        String name,
        String contactName,
        String email,
        String phone,
        String address,
        String customerType,
        String taxCode,
        String notes,
        String status,
        String createdAt,
        String updatedAt
    ) {}

    private Customer row(ResultSet r, int n) throws SQLException {
        return new Customer(
            r.getInt("customer_id"),
            r.getString("customer_name"),
            r.getString("contact_name"),
            r.getString("email"),
            r.getString("phone"),
            r.getString("address"),
            r.getString("customer_type"),
            r.getString("tax_code"),
            r.getString("notes"),
            r.getString("status"),
            r.getTimestamp("created_at") != null ? r.getTimestamp("created_at").toInstant().toString() : null,
            r.getTimestamp("updated_at") != null ? r.getTimestamp("updated_at").toInstant().toString() : null
        );
    }

    public List<Customer> listCustomers(int offset, int limit, String search, String status, String customerType) {
        StringBuilder sql = new StringBuilder("""
            SELECT * FROM public.customer
            WHERE (strpos(lower(customer_name), lower(?)) > 0
               OR strpos(lower(COALESCE(contact_name, '')), lower(?)) > 0
               OR strpos(lower(COALESCE(phone, '')), lower(?)) > 0)
        """);
        List<Object> args = new java.util.ArrayList<>(List.of(search, search, search));
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (customerType != null && !customerType.isBlank()) {
            sql.append(" AND customer_type = ?");
            args.add(customerType);
        }
        sql.append(" ORDER BY customer_id LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);

        return db.query(sql.toString(), this::row, args.toArray());
    }

    public long countCustomers(String search, String status, String customerType) {
        StringBuilder sql = new StringBuilder("""
            SELECT count(*) FROM public.customer
            WHERE (strpos(lower(customer_name), lower(?)) > 0
               OR strpos(lower(COALESCE(contact_name, '')), lower(?)) > 0
               OR strpos(lower(COALESCE(phone, '')), lower(?)) > 0)
        """);
        List<Object> args = new java.util.ArrayList<>(List.of(search, search, search));
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (customerType != null && !customerType.isBlank()) {
            sql.append(" AND customer_type = ?");
            args.add(customerType);
        }
        return db.queryForObject(sql.toString(), Long.class, args.toArray());
    }

    public Optional<Customer> customer(int id) {
        return db.query("SELECT * FROM public.customer WHERE customer_id = ?", this::row, id).stream().findFirst();
    }

    public int createCustomer(String name, String contactName, String email, String phone,
                              String address, String customerType, String taxCode, String notes) {
        return db.queryForObject("""
            INSERT INTO public.customer(customer_name, contact_name, email, phone, address, customer_type, tax_code, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING customer_id
        """, Integer.class, name, contactName, email, phone, address, customerType, taxCode, notes);
    }

    public void updateCustomer(int id, String name, String contactName, String email, String phone,
                              String address, String customerType, String taxCode, String notes) {
        db.update("""
            UPDATE public.customer
            SET customer_name = ?, contact_name = ?, email = ?, phone = ?, address = ?,
                customer_type = ?, tax_code = ?, notes = ?, updated_at = CURRENT_TIMESTAMP
            WHERE customer_id = ?
        """, name, contactName, email, phone, address, customerType, taxCode, notes, id);
    }

    public void customerStatus(int id, String status) {
        db.update("UPDATE public.customer SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE customer_id = ?", status, id);
    }

    public boolean hasOpenOrders(int customerId) {
        Integer count = db.queryForObject("""
            SELECT count(*) FROM public.sales_order
            WHERE customer_id = ? AND status IN ('PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED')
        """, Integer.class, customerId);
        return count != null && count > 0;
    }

    public void auditAction(int actor, String entityType, int entityId, String action, String requestId) {
        db.update("INSERT INTO public.audit_log(actor_user_id,event_at,entity_type,entity_key,action,request_id) VALUES (?,CURRENT_TIMESTAMP,?,?,?,?)",
            actor, entityType, Integer.toString(entityId), action, requestId);
    }
}
