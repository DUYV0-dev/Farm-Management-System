-- Synthetic data for the isolated backup/recovery test cluster only.
BEGIN;
INSERT INTO public.app_user(username,password_hash,full_name,email) VALUES
 ('recovery.owner','synthetic-test-hash','Chủ trang trại','owner@recovery.invalid'),
 ('recovery.worker','synthetic-test-hash','Nhân viên','worker@recovery.invalid');
INSERT INTO public.farm(farm_name,address,area_ha,created_by,owner_user_id) VALUES
 ('Trang trại khôi phục','Cần Thơ',12.50,1,1),('Trang trại thứ hai','Đà Lạt',5.25,2,2);
INSERT INTO public.farm_member(farm_id,user_id) VALUES (1,1),(2,2);
INSERT INTO public.farm_member_role(farm_id,user_id,role_id)
 SELECT f.farm_id,f.owner_user_id,r.role_id FROM public.farm f CROSS JOIN public.role r
 WHERE r.role_name='OWNER' AND r.scope='FARM';
INSERT INTO public.plot(farm_id,plot_name,area_ha,created_by) VALUES (1,'Lô lúa',2.75,1),(2,'Lô rau',1.25,2);
INSERT INTO public.crop(crop_name,tenant_farm_id) VALUES ('Lúa',1),('Rau',2);
INSERT INTO public.crop_season(plot_id,crop_id,season_name,start_date,end_date,status,description)
 VALUES (1,1,'Đông xuân',DATE '2026-01-01',DATE '2026-04-30','IN_PROGRESS',E'Dòng 1\nDòng 2\t"ghi chú"'),
        (2,2,'Vụ rau',DATE '2026-01-01',DATE '2026-03-31','IN_PROGRESS',NULL);
INSERT INTO public.material_category(category_name,tenant_farm_id) VALUES ('Phân bón',1);
INSERT INTO public.material(category_id,material_name,unit,tenant_farm_id) VALUES (1,'Phân hữu cơ','kg',1);
INSERT INTO public.warehouse(farm_id,warehouse_name) VALUES (1,'Kho chính');
INSERT INTO public.warehouse_inventory(warehouse_id,material_id,quantity) VALUES (1,1,125.375);
INSERT INTO public.material_usage(season_id,material_id,material_name,usage_type,quantity,unit,application_date,created_by)
 VALUES (1,1,'Phân hữu cơ','FERTILIZER',2.125,'kg',DATE '2026-01-02',1);
INSERT INTO public.farm_expense(farm_id,season_id,category,amount,expense_date,description,created_by)
 VALUES (1,1,'FERTILIZER',123456.78,DATE '2026-01-02','Chi phí thử nghiệm',1);
CREATE TABLE public.recovery_probe (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 text_value TEXT, json_value JSONB, binary_value BYTEA,
 recorded_at TIMESTAMPTZ, amount NUMERIC(18,6)
);
INSERT INTO public.recovery_probe(text_value,json_value,binary_value,recorded_at,amount) VALUES
 (E'Tiếng Việt\nDòng mới\tTab\rCR', '{"unicode":"Cần Thơ","null":null,"array":[1,2]}',
  decode('000aff805c','hex'),TIMESTAMPTZ '2026-01-02 12:34:56.123456+07',123.456789),
 (NULL,NULL,NULL,NULL,NULL),('','{}',decode('','hex'),TIMESTAMPTZ '2026-01-01 00:00:00+00',0);
SELECT lo_from_bytea(0,decode(repeat('000aff805c',1000),'hex'));
COMMIT;
