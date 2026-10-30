-- Proforma and tax invoices: company GSTIN, HSN per item, and the document an invoice was made from.
ALTER TABLE public.app_user ADD COLUMN gstin character varying(15);
ALTER TABLE public.quote_item ADD COLUMN hsn character varying(8);
ALTER TABLE public.quotation ADD COLUMN source_id bigint;
ALTER TABLE public.quotation ADD COLUMN po_no character varying(255);
ALTER TABLE public.quotation ADD COLUMN due_date date;
