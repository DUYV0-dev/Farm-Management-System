-- Isolated integration-test cluster ONLY; not an application installation schema.
DO $$ BEGIN
 IF current_database()<>'management_test' OR inet_server_port()<>55432 THEN
  RAISE EXCEPTION 'This fixture requires management_test on port 55432';
 END IF;
END $$;
\ir ../../../database/00_Identity_Schema.sql
