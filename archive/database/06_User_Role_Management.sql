-- Run once as the database owner, after 05_Authentication_Sessions.sql.
-- Uses the existing ERD app_user, role, user_role and audit_log tables.
BEGIN;
CREATE TABLE public.auth_permission (
    permission_code VARCHAR(80) PRIMARY KEY,
    description VARCHAR(255) NOT NULL
);
CREATE TABLE public.auth_role_permission (
    role_id INTEGER NOT NULL REFERENCES public.role(role_id) ON DELETE RESTRICT,
    permission_code VARCHAR(80) NOT NULL REFERENCES public.auth_permission(permission_code) ON DELETE RESTRICT,
    PRIMARY KEY(role_id, permission_code)
);
INSERT INTO public.auth_permission VALUES
 ('users:read', 'View and search system users'),
 ('users:create', 'Create users with the USER role'),
 ('users:update', 'Update user profile information'),
 ('users:status', 'Activate or deactivate users'),
 ('users:assign', 'Assign system roles to users'),
 ('users:password', 'Reset another user password'),
 ('roles:read', 'View system roles and permission catalog'),
 ('roles:write', 'Create and update system roles'),
 ('roles:permissions', 'Manage system role permissions');
INSERT INTO public.role(role_name, scope, description)
VALUES ('SYSTEM_ADMIN', 'SYSTEM', 'System administrator'), ('USER', 'SYSTEM', 'Standard user')
ON CONFLICT(scope, role_name) DO NOTHING;
INSERT INTO public.auth_role_permission(role_id, permission_code)
SELECT r.role_id, p.permission_code FROM public.role r CROSS JOIN public.auth_permission p
WHERE r.scope = 'SYSTEM' AND r.role_name = 'SYSTEM_ADMIN';
-- Never automatically promote an existing account. See Bootstrap-Admin.sql.
GRANT SELECT ON public.auth_permission TO farm_app;
GRANT SELECT, INSERT, DELETE ON public.auth_role_permission TO farm_app;
GRANT SELECT, INSERT, UPDATE ON public.app_user, public.role TO farm_app;
GRANT SELECT, INSERT, DELETE ON public.user_role TO farm_app;
GRANT INSERT ON public.audit_log TO farm_app;
-- Grant only the sequences used by these existing tables (works with SERIAL/IDENTITY).
DO $$
DECLARE seq TEXT;
BEGIN
    FOREACH seq IN ARRAY ARRAY[
        pg_get_serial_sequence('public.app_user', 'user_id'),
        pg_get_serial_sequence('public.role', 'role_id')
    ] LOOP
        IF seq IS NOT NULL THEN EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE %s TO farm_app', seq); END IF;
    END LOOP;
END $$;
-- Resolve the audit sequence without assuming its primary key column name.
DO $$
DECLARE item RECORD; seq TEXT;
BEGIN
    FOR item IN SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'audit_log' LOOP
        seq := pg_get_serial_sequence('public.audit_log', item.column_name);
        IF seq IS NOT NULL THEN EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE %s TO farm_app', seq); END IF;
    END LOOP;
END $$;
COMMIT;
