package com.saas.quote;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** One row per sign-in. The JWT carries this id; signing out stamps signedOutAt and the token stops working. */
@Entity @Getter @Setter @Table(indexes = {@Index(columnList = "tenantId,signedInAt"), @Index(columnList = "userId")})
public class LoginSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long tenantId;
    private Long userId;
    private String userName;
    private String userEmail;
    private String portal;            // ADMIN | EMPLOYEE
    private Instant signedInAt;
    private Instant signedOutAt;      // null while active
    private String signOutReason;     // SIGN_OUT | EXPIRED is not stored (derived); PASSWORD_RESET / DEACTIVATED are
    private String ip;
    @Column(length = 300) private String userAgent;
}
