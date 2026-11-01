ALTER TABLE public.app_user ADD COLUMN phone VARCHAR(20) NOT NULL DEFAULT '';
ALTER TABLE public.app_user ADD COLUMN bio VARCHAR(500) NOT NULL DEFAULT '';
-- Staff setup is optional for existing owners; ownership and permissions are unchanged.
UPDATE public.farm SET onboarding_status='READY' WHERE onboarding_status='STAFF';

CREATE TABLE public.product_listing (
 listing_id SERIAL PRIMARY KEY, farm_id INTEGER NOT NULL REFERENCES public.farm,
 product_id INTEGER NOT NULL UNIQUE REFERENCES public.harvested_product,
 public_name VARCHAR(150) NOT NULL CHECK(length(btrim(public_name))>0),
 description VARCHAR(2000) NOT NULL DEFAULT '', price NUMERIC(15,2) NOT NULL CHECK(price>0 AND price<=999999999.99),
 published BOOLEAN NOT NULL DEFAULT false, version INTEGER NOT NULL DEFAULT 1,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE public.store_order (
 order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(), request_key UUID NOT NULL UNIQUE,
 farm_id INTEGER NOT NULL REFERENCES public.farm, buyer_name VARCHAR(100) NOT NULL,
 phone VARCHAR(20) NOT NULL, email VARCHAR(254) NOT NULL, address VARCHAR(500) NOT NULL,
 note VARCHAR(500) NOT NULL, payload JSONB NOT NULL,
 total_amount NUMERIC(15,2) NOT NULL CHECK(total_amount>0),
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','ACCEPTED','REJECTED')),
 sales_order_id INTEGER UNIQUE REFERENCES public.sales_order, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE public.store_order_item (
 item_id SERIAL PRIMARY KEY, order_id UUID NOT NULL REFERENCES public.store_order,
 listing_id INTEGER NOT NULL REFERENCES public.product_listing, product_id INTEGER NOT NULL REFERENCES public.harvested_product,
 product_name VARCHAR(150) NOT NULL, unit VARCHAR(20),
 quantity NUMERIC(16,3) NOT NULL CHECK(quantity>0), unit_price NUMERIC(15,2) NOT NULL CHECK(unit_price>0),
 total_price NUMERIC(15,2) NOT NULL CHECK(total_price>0), UNIQUE(order_id,listing_id)
);
CREATE INDEX ix_store_order_farm_status ON public.store_order(farm_id,status,created_at DESC);
ALTER TABLE public.product_listing ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_scope ON public.product_listing USING(public.can_access_farm(farm_id));
ALTER TABLE public.store_order ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_scope ON public.store_order USING(public.can_access_farm(farm_id));
ALTER TABLE public.store_order_item ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_scope ON public.store_order_item USING(EXISTS(SELECT 1 FROM public.store_order o WHERE o.order_id=store_order_item.order_id));
GRANT SELECT,INSERT,UPDATE ON public.product_listing,public.store_order TO farm_app;
GRANT SELECT ON public.store_order_item TO farm_app;
GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO farm_app;

CREATE FUNCTION public.listing_relationship_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM public.harvested_product p JOIN public.crop_production c USING(production_id)
 WHERE p.product_id=NEW.product_id AND c.farm_id=NEW.farm_id) THEN
 RAISE EXCEPTION 'Listing product must belong to the selected farm' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER listing_relationship BEFORE INSERT OR UPDATE ON public.product_listing FOR EACH ROW EXECUTE FUNCTION public.listing_relationship_guard();

-- The public surface deliberately exposes only published merchandise, never buyer/account details.
CREATE FUNCTION public.store_catalog() RETURNS TABLE(id INTEGER,farm_id INTEGER,farm_name VARCHAR,name VARCHAR,description VARCHAR,price NUMERIC,quantity NUMERIC,unit VARCHAR,version INTEGER)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 SELECT l.listing_id,l.farm_id,f.farm_name,l.public_name,l.description,l.price,p.quantity,c.unit,l.version
 FROM public.product_listing l JOIN public.harvested_product p USING(product_id)
 JOIN public.crop_production c USING(production_id) JOIN public.farm f ON f.farm_id=l.farm_id
 WHERE l.published AND f.status='ACTIVE' AND p.status='AVAILABLE' AND p.quantity>0 AND c.farm_id=l.farm_id
$$;
REVOKE ALL ON FUNCTION public.store_catalog() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.store_catalog() TO farm_app;

CREATE FUNCTION public.store_checkout(b JSONB) RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE existing public.store_order%ROWTYPE; item JSONB; listing RECORD; farm INTEGER; order_key UUID; quantity NUMERIC; total NUMERIC:=0; count_items INTEGER;
BEGIN
 -- A single request key serializes retries, including simultaneous submissions.
 PERFORM pg_advisory_xact_lock(hashtextextended(b->>'requestKey',8818));
 SELECT * INTO existing FROM public.store_order WHERE request_key=(b->>'requestKey')::UUID;
 IF FOUND THEN
  IF existing.payload<>b THEN RAISE EXCEPTION 'Store request key reused' USING ERRCODE='23514'; END IF;
  RETURN existing.order_id;
 END IF;
 count_items:=jsonb_array_length(b->'items');
 IF count_items<1 OR count_items>50 THEN RAISE EXCEPTION 'Store invalid cart' USING ERRCODE='23514'; END IF;
 IF (SELECT count(DISTINCT i->>'listingId') FROM jsonb_array_elements(b->'items') i)<>count_items THEN
  RAISE EXCEPTION 'Store invalid cart' USING ERRCODE='23514'; END IF;
 -- Price/publication and product quantities remain stable while the request is recorded.
 -- All flows lock products before listings, in product order, to avoid deadlocks.
 PERFORM p.product_id FROM public.harvested_product p JOIN public.product_listing l USING(product_id)
 WHERE l.listing_id IN (SELECT (i->>'listingId')::INTEGER FROM jsonb_array_elements(b->'items') i)
 ORDER BY p.product_id FOR UPDATE OF p;
 PERFORM l.listing_id FROM public.product_listing l
 WHERE l.listing_id IN (SELECT (i->>'listingId')::INTEGER FROM jsonb_array_elements(b->'items') i)
 ORDER BY l.listing_id FOR UPDATE OF l;
 FOR item IN SELECT value FROM jsonb_array_elements(b->'items') LOOP
  SELECT * INTO listing FROM public.store_catalog() WHERE id=(item->>'listingId')::INTEGER;
  quantity:=(item->>'quantity')::NUMERIC;
  IF NOT FOUND OR quantity<=0 OR quantity>listing.quantity OR quantity<>round(quantity,3) THEN
   RAISE EXCEPTION 'Store insufficient stock' USING ERRCODE='23514'; END IF;
  IF listing.version<>(item->>'version')::INTEGER THEN RAISE EXCEPTION 'Store price changed' USING ERRCODE='23514'; END IF;
  IF farm IS NOT NULL AND farm<>listing.farm_id THEN RAISE EXCEPTION 'Store mixed farms' USING ERRCODE='23514'; END IF;
  farm:=listing.farm_id;total:=total+round(quantity*listing.price,2);
 END LOOP;
 IF total<=0 OR total>9999999999999.99 THEN RAISE EXCEPTION 'Store invalid cart' USING ERRCODE='23514'; END IF;
 INSERT INTO public.store_order(request_key,farm_id,buyer_name,phone,email,address,note,payload,total_amount)
 VALUES((b->>'requestKey')::UUID,farm,b->>'buyerName',b->>'phone',b->>'email',b->>'address',b->>'note',b,total) RETURNING order_id INTO order_key;
 FOR item IN SELECT value FROM jsonb_array_elements(b->'items') LOOP
  SELECT * INTO listing FROM public.store_catalog() WHERE id=(item->>'listingId')::INTEGER;
  INSERT INTO public.store_order_item(order_id,listing_id,product_id,product_name,unit,quantity,unit_price,total_price)
  SELECT order_key,l.listing_id,l.product_id,l.public_name,listing.unit,(item->>'quantity')::NUMERIC,l.price,round((item->>'quantity')::NUMERIC*l.price,2)
  FROM public.product_listing l WHERE l.listing_id=listing.id;
 END LOOP;
 RETURN order_key;
END $$;
REVOKE ALL ON FUNCTION public.store_checkout(JSONB) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.store_checkout(JSONB) TO farm_app;

CREATE FUNCTION public.store_receipt(target UUID) RETURNS JSONB
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 SELECT jsonb_build_object('id',o.order_id,'farmName',f.farm_name,'status',COALESCE(so.status,o.status),
 'totalAmount',o.total_amount,'createdAt',o.created_at,'items',
 (SELECT jsonb_agg(jsonb_build_object('name',i.product_name,'quantity',i.quantity,'unit',i.unit,'unitPrice',i.unit_price,'totalPrice',i.total_price))
 FROM public.store_order_item i WHERE i.order_id=o.order_id))
 FROM public.store_order o JOIN public.farm f USING(farm_id) LEFT JOIN public.sales_order so ON so.so_id=o.sales_order_id WHERE o.order_id=target
$$;
REVOKE ALL ON FUNCTION public.store_receipt(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.store_receipt(UUID) TO farm_app;
