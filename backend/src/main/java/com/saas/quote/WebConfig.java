package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import jakarta.annotation.PostConstruct;

@Configuration @RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final PermissionInterceptor permissions;
    @Value("${app.proxy-hops:1}") private int hops;

    @PostConstruct void init() { ClientIp.hops = Math.max(1, hops); }

    @Override public void addInterceptors(InterceptorRegistry r) { r.addInterceptor(permissions).addPathPatterns("/api/**"); }
}
