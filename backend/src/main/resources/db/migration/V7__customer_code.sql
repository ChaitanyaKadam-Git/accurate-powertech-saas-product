-- Customer codes (CUST-0001 ...) so a customer is tracked by code across enquiries and statements.
ALTER TABLE public.customer ADD COLUMN code character varying(20);
UPDATE public.customer c SET code = 'CUST-' || lpad(x.n::text, 4, '0')
FROM (SELECT id, row_number() OVER (PARTITION BY tenant_id ORDER BY id) AS n FROM public.customer) x
WHERE x.id = c.id;
ALTER TABLE public.customer ALTER COLUMN code SET NOT NULL;
CREATE UNIQUE INDEX ux_customer_tenant_code ON public.customer (tenant_id, lower(code));

-- Statements remember their customer. Existing ones: from their enquiry, else the one customer with the same name.
ALTER TABLE public.quotation ADD COLUMN customer_id bigint REFERENCES public.customer (id);
UPDATE public.quotation q SET customer_id = e.customer_id FROM public.enquiry e WHERE q.enquiry_id = e.id;
UPDATE public.quotation q SET customer_id = m.id
FROM (SELECT tenant_id, lower(trim(name)) AS lname, min(id) AS id, count(*) AS n FROM public.customer GROUP BY 1, 2) m
WHERE q.customer_id IS NULL AND m.n = 1 AND m.tenant_id = q.owner_id AND m.lname = lower(trim(q.client_name));
CREATE INDEX idx_quotation_customer ON public.quotation (customer_id);
