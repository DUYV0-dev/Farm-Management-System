package com.farmmanagement.backend.auth;

import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Profile("seed")
public class SeedAccount implements CommandLineRunner {
    private final org.springframework.context.ConfigurableApplicationContext context;
    private final JdbcTemplate db; private final PasswordEncoder encoder; private final TransactionTemplate tx;
    public SeedAccount(JdbcTemplate db,PasswordEncoder encoder,TransactionTemplate tx,org.springframework.context.ConfigurableApplicationContext context) { this.context=context;this.db=db;this.encoder=encoder;this.tx=tx; }
    public void run(String... args) {
        String username=System.getenv("SEED_USERNAME"),password=System.getenv("SEED_PASSWORD"),email=System.getenv("SEED_EMAIL");
        if(username==null || !username.matches("[a-z0-9._-]{3,50}") || password==null || password.length()<12 || password.length()>128
            || !password.matches("(?s).*\\p{L}.*") || !password.matches("(?s).*\\d.*") || email==null || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length()>254)
            throw new IllegalStateException("Set valid SEED_USERNAME, SEED_PASSWORD (12-128, letters+digits), SEED_EMAIL");
        tx.executeWithoutResult(status->{
            if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM public.app_user WHERE username=? OR email=?)",Boolean.class,username,email)))
                throw new IllegalStateException("Seed user/email already exists; existing account was not changed");
            int role=db.queryForObject("""
                INSERT INTO public.role(role_name,scope,description) VALUES ('USER','SYSTEM','Development login account')
                ON CONFLICT(scope,role_name) DO UPDATE SET role_name=EXCLUDED.role_name RETURNING role_id
                """,Integer.class);
            int uid=db.queryForObject("INSERT INTO public.app_user(username,password_hash,full_name,email,status) VALUES (?,?,?,?, 'ACTIVE') RETURNING user_id",
                Integer.class,username,encoder.encode(password),"Tài khoản kiểm thử",email);
            db.update("INSERT INTO public.user_role(user_id,role_id) VALUES (?,?)",uid,role);
            String rid=UUID.randomUUID().toString();
            db.update("INSERT INTO public.audit_log(actor_user_id,event_at,entity_type,entity_key,action,request_id) VALUES (NULL,CURRENT_TIMESTAMP,'USER',?,'CREATE',?)",Integer.toString(uid),rid);
            db.update("INSERT INTO public.audit_log(actor_user_id,event_at,entity_type,entity_key,action,request_id) VALUES (NULL,CURRENT_TIMESTAMP,'USER_ROLE',?,'ROLE_CHANGE',?)",uid+":"+role,rid);
        });
        System.out.println("SEED SUCCESS: account and SYSTEM USER role created.");
        org.springframework.boot.SpringApplication.exit(context,()->0);
    }
}
