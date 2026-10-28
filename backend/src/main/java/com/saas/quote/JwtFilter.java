package com.saas.quote;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

/** Valid signature + open session + active user => authenticated. Anything else stays anonymous and gets a 401. */
@Component @RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final SessionRepo sessions;
    private final UserRepo users;
    private final AccessService access;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            try {
                JwtService.Parsed p = jwt.parse(h.substring(7));
                LoginSession s = sessions.findById(p.sessionId()).orElse(null);
                AppUser u = (s == null || s.getSignedOutAt() != null || !s.getUserId().equals(p.userId())) ? null : users.findById(p.userId()).orElse(null);
                if (u != null && u.enabled()) {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(access.principal(u, s.getId()), null, List.of()));
                }
            } catch (Exception ignored) { /* invalid/expired token -> stays unauthenticated -> 401 */ }
        }
        chain.doFilter(req, res);
    }
}
