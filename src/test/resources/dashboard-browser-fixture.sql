-- TEST DATABASE ONLY: run after the integration tests; never run against a real farm database.
DO $$
DECLARE actor INTEGER; farm INTEGER; plot INTEGER; crop INTEGER; season INTEGER; sysrole INTEGER; unit INTEGER; category INTEGER; material INTEGER; warehouse INTEGER;
BEGIN
 INSERT INTO app_user(username,password_hash,full_name,email)
 SELECT 'dashboard-browser',password_hash,'Vinh kiểm thử','dashboard-browser@test.invalid' FROM app_user WHERE username ~ '^t[0-9a-f]{12}$' ORDER BY user_id LIMIT 1 RETURNING user_id INTO actor;
 IF actor IS NULL THEN RAISE EXCEPTION 'No integration password fixture'; END IF;
 INSERT INTO role(role_name,scope) VALUES ('DASHBOARD_BROWSER','SYSTEM') RETURNING role_id INTO sysrole;
 INSERT INTO user_role VALUES(actor,sysrole);
 INSERT INTO auth_role_permission SELECT sysrole,permission_code FROM auth_permission WHERE permission_code IN ('dashboard:read','reports:read','reports:export:pdf','reports:export:xlsx','expenses:read','harvests:read','crops:read','warehouses:read');
 INSERT INTO farm(farm_name,address,area_ha) VALUES ('Trang trại kiểm thử Dashboard','Cần Thơ',10) RETURNING farm_id INTO farm;
 INSERT INTO plot(farm_id,plot_name,area_ha) VALUES (farm,'Khu lúa',6) RETURNING plot_id INTO plot;
 INSERT INTO crop(crop_name) VALUES ('Lúa kiểm thử dashboard') RETURNING crop_id INTO crop;
 SELECT unit_id INTO unit FROM public.unit WHERE unit_code='kg';
 INSERT INTO crop_season(plot_id,crop_id,status,season_name,planned_start_date,planned_end_date,yield_unit_id,expected_yield) VALUES(plot,crop,'IN_PROGRESS','Mùa vụ 2020','2020-01-01','2020-12-31',unit,250) RETURNING season_id INTO season;
 INSERT INTO farm_member(farm_id,user_id) VALUES(farm,actor);
 INSERT INTO farm_member_role SELECT farm,actor,role_id FROM role WHERE role_name='MANAGER' AND scope='FARM';
 INSERT INTO harvest(season_id,created_by,harvest_date,quantity,quality) VALUES (season,actor,'2020-05-01',100,'Loại A'),(season,actor,'2020-06-01',120,'Loại A'),(season,actor,'2020-06-30',80,'Loại B');
 INSERT INTO material_category(category_name) VALUES('Vật tư kiểm thử dashboard') RETURNING category_id INTO category;
 INSERT INTO material(category_id,material_name,unit) VALUES(category,'Phân bón kiểm thử','kg') RETURNING material_id INTO material;
 INSERT INTO warehouse(farm_id,warehouse_name) VALUES(farm,'Kho kiểm thử dashboard') RETURNING warehouse_id INTO warehouse;
 INSERT INTO warehouse_inventory(warehouse_id,material_id,quantity) VALUES(warehouse,material,125.125);
END $$;
