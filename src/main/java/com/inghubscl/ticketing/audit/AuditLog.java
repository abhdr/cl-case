package com.inghubscl.ticketing.audit;

import com.inghubscl.ticketing.common.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "audit_logs")
public class AuditLog extends Auditable {

  @Id @UuidGenerator private UUID id;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(nullable = false, length = 100)
  private String action;

  @Column(name = "resource_type", nullable = false, length = 50)
  private String resourceType;

  @Column(name = "resource_id")
  private String resourceId;

  @Column(length = 45)
  private String ip;

  @Column(name = "user_agent", length = 500)
  private String userAgent;

  protected AuditLog() {}

  public AuditLog(
      UUID actorId,
      String action,
      String resourceType,
      String resourceId,
      String ip,
      String userAgent) {
    this.actorId = actorId;
    this.action = action;
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.ip = ip;
    this.userAgent = userAgent;
  }

  public UUID getId() {
    return id;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getAction() {
    return action;
  }

  public String getResourceType() {
    return resourceType;
  }

  public String getResourceId() {
    return resourceId;
  }

  public String getIp() {
    return ip;
  }

  public String getUserAgent() {
    return userAgent;
  }
}
