package com.inghubscl.ticketing.audit;

import com.inghubscl.ticketing.common.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

  private static final Logger log = LoggerFactory.getLogger(AuditService.class);

  private final AuditLogRepository repository;

  public AuditService(AuditLogRepository repository) {
    this.repository = repository;
  }

  @Async
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(String action, String resourceType, String resourceId) {
    try {
      UUID actorId = SecurityUtils.currentUserId().orElse(null);
      String ip = null;
      String userAgent = null;
      RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
      if (attrs instanceof ServletRequestAttributes sra) {
        HttpServletRequest req = sra.getRequest();
        ip = clientIp(req);
        userAgent = req.getHeader("User-Agent");
      }
      AuditLog entry = new AuditLog(actorId, action, resourceType, resourceId, ip, userAgent);
      repository.save(entry);
    } catch (Exception ex) {
      log.warn("Failed to write audit log for action={} resource={}", action, resourceType, ex);
    }
  }

  private static String clientIp(HttpServletRequest req) {
    String fwd = req.getHeader("X-Forwarded-For");
    if (fwd != null && !fwd.isBlank()) {
      return fwd.split(",")[0].trim();
    }
    return req.getRemoteAddr();
  }
}
