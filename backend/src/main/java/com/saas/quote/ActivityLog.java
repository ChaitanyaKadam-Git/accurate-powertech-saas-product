package com.saas.quote;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** Audit trail: who did what, on which record, from which IP. */
@Entity @Getter @Setter @Table(indexes = {@Index(columnList = "tenantId,at"), @Index(columnList = "userId")})
public class ActivityLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long tenantId;
    private Long userId;
    private String userName;
    private String module;            // a AppModule name, or AUTH
    private String action;            // CREATE | EDIT | DELETE | DOWNLOAD | SEND | ACTIVATE | DEACTIVATE | RESET_PASSWORD | PERMISSIONS | IMPORT | LOGIN_FAILED ...
    private String entityType;
    private String entityId;
    @Column(length = 1000) private String detail;
    private String ip;
    private Instant at;
}
