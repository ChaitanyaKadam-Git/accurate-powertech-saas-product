package com.saas.quote;

import java.lang.annotation.*;

/** Endpoint is only for users whose role is the Admin role (admin console APIs). */
@Target({ElementType.METHOD, ElementType.TYPE}) @Retention(RetentionPolicy.RUNTIME)
public @interface AdminOnly {}
