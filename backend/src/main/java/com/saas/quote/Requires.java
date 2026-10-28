package com.saas.quote;

import java.lang.annotation.*;

/** Endpoint needs this right on this module. Enforced by PermissionInterceptor for every request. */
@Target({ElementType.METHOD, ElementType.TYPE}) @Retention(RetentionPolicy.RUNTIME)
public @interface Requires {
    AppModule module();
    Right right();
}
