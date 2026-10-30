-- Enquiries and Parts got their own rights (they used the Customers and Products rights). Everyone keeps exactly what they
-- could do: each CUSTOMERS right is copied to ENQUIRIES and each PRODUCTS right to PARTS, in roles and per-user overrides.
INSERT INTO public.role_right (role_id, right_key)
SELECT role_id, replace(right_key, 'CUSTOMERS:', 'ENQUIRIES:') FROM public.role_right WHERE right_key LIKE 'CUSTOMERS:%'
UNION
SELECT role_id, replace(right_key, 'PRODUCTS:', 'PARTS:') FROM public.role_right WHERE right_key LIKE 'PRODUCTS:%';

INSERT INTO public.user_grant (user_id, right_key)
SELECT user_id, replace(right_key, 'CUSTOMERS:', 'ENQUIRIES:') FROM public.user_grant WHERE right_key LIKE 'CUSTOMERS:%'
UNION
SELECT user_id, replace(right_key, 'PRODUCTS:', 'PARTS:') FROM public.user_grant WHERE right_key LIKE 'PRODUCTS:%';

INSERT INTO public.user_revoke (user_id, right_key)
SELECT user_id, replace(right_key, 'CUSTOMERS:', 'ENQUIRIES:') FROM public.user_revoke WHERE right_key LIKE 'CUSTOMERS:%'
UNION
SELECT user_id, replace(right_key, 'PRODUCTS:', 'PARTS:') FROM public.user_revoke WHERE right_key LIKE 'PRODUCTS:%';
