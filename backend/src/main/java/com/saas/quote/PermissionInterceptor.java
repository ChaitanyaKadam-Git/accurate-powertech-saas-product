package com.saas.quote;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/** Enforces @AdminOnly / @Requires on every API call, plus the "set your password first" gate. */
@Component
public class PermissionInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) return true;
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof AuthUser me)) return true;   // public endpoints (auth, health)

        String path = req.getRequestURI();
        boolean allowedWhileLocked = path.startsWith("/api/account") || path.equals("/api/me") || path.equals("/api/auth/logout");
        if (me.mustChangePassword() && !allowedWhileLocked)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED");

        if (hm.hasMethodAnnotation(AdminOnly.class) || hm.getBeanType().isAnnotationPresent(AdminOnly.class)) {
            if (!me.admin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrators only");
        }
        Requires r = hm.getMethodAnnotation(Requires.class);
        if (r == null) r = hm.getBeanType().getAnnotation(Requires.class);
        if (r != null && !me.can(r.module(), r.right()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not have permission to " + r.right().name().toLowerCase() + " (" + r.module().label + ")");
        return true;
    }
}
