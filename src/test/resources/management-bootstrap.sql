-- Isolated integration-test cluster ONLY; not an application installation schema.
DO $$ BEGIN
 IF current_database()<>'management_test' OR inet_server_port()<>55432 THEN
  RAISE EXCEPTION 'This fixture requires management_test on port 55432';
 END IF;
END $$;
CREATE ROLE farm_app LOGIN;
CREATE TABLE public.app_user (
 user_id SERIAL PRIMARY KEY, username VARCHAR(50) NOT NULL UNIQUE,
 password_hash TEXT NOT NULL, full_name VARCHAR(100) NOT NULL, email VARCHAR(254) NOT NULL UNIQUE,
 status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE', created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE public.role (
 role_id SERIAL PRIMARY KEY, role_name VARCHAR(50) NOT NULL, scope VARCHAR(10) NOT NULL,
 description VARCHAR(255), UNIQUE(scope,role_name)
);
CREATE TABLE public.user_role (
 user_id INTEGER REFERENCES public.app_user(user_id), role_id INTEGER REFERENCES public.role(role_id),
 PRIMARY KEY(user_id,role_id)
);
CREATE TABLE public.audit_log (
 audit_id SERIAL PRIMARY KEY, actor_user_id INTEGER REFERENCES public.app_user(user_id),
 event_at TIMESTAMPTZ NOT NULL, entity_type VARCHAR(50), entity_key VARCHAR(100), action VARCHAR(50), request_id VARCHAR(100)
);
GRANT SELECT ON public.audit_log TO postgres;
