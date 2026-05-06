package com.inghubscl.ticketing.common;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

  public static final String ROLE_ADMIN = "ROLE_ADMIN";

  private SecurityUtils() {}

  public static Optional<UUID> currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(auth.getName()));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  public static UUID requireCurrentUserId() {
    return currentUserId()
        .orElseThrow(() -> new IllegalStateException("No authenticated user in security context"));
  }

  public static Set<String> currentRoles() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      return Set.of();
    }
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toUnmodifiableSet());
  }

  public static boolean isAdmin() {
    return currentRoles().contains(ROLE_ADMIN);
  }
}
