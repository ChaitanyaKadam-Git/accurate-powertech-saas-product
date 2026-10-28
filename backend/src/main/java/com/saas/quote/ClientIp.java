package com.saas.quote;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Client IP for the activity log. X-Forwarded-For can be forged by the caller, so only the entry added by OUR proxy is trusted:
 * `hops` = number of proxies in front of the backend (1 for nginx or an ALB, 2 for ALB then nginx).
 */
public final class ClientIp {
    static int hops = 1;
    private ClientIp() {}

    public static String of(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] p = xff.split(",");
            int i = p.length - hops;
            if (i >= 0) return p[i].trim();
        }
        return req.getRemoteAddr();
    }

    public static String current() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a ? of(a.getRequest()) : null;
    }

    public static String agent() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
            String ua = a.getRequest().getHeader("User-Agent");
            return ua == null ? null : ua.substring(0, Math.min(ua.length(), 290));
        }
        return null;
    }
}
