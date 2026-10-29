-- Business lines: every statement and every standard-text section belongs to one (SOLAR, GENSET, ...).
-- Everything that exists today was written for solar.
ALTER TABLE public.quotation ADD COLUMN business_line character varying(20) NOT NULL DEFAULT 'SOLAR';
ALTER TABLE public.fixed_section ADD COLUMN business_line character varying(20) NOT NULL DEFAULT 'SOLAR';
