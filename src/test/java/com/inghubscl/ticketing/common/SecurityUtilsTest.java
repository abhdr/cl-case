package com.inghubscl.ticketing.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityUtilsTest {

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void currentUserIdIsEmptyWhenNoAuthentication() {
    assertThat(SecurityUtils.currentUserId()).isEmpty();
  }

  @Test
  void currentUserIdIsEmptyForAnonymousPrincipal() {
    var auth =
        new AnonymousAuthenticationToken(
            "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThat(SecurityUtils.currentUserId()).isEmpty();
  }

  @Test
  void currentUserIdReturnsParsedUuid() {
    UUID id = UUID.randomUUID();
    var auth =
        new UsernamePasswordAuthenticationToken(
            id.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThat(SecurityUtils.currentUserId()).contains(id);
  }

  @Test
  void currentUserIdIsEmptyForNonUuidName() {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "not-a-uuid", null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThat(SecurityUtils.currentUserId()).isEmpty();
  }

  @Test
  void requireCurrentUserIdThrowsWhenAbsent() {
    assertThatThrownBy(SecurityUtils::requireCurrentUserId)
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void currentRolesEmptyWhenNoAuthentication() {
    assertThat(SecurityUtils.currentRoles()).isEmpty();
  }

  @Test
  void currentRolesReturnsAuthorities() {
    var auth =
        new UsernamePasswordAuthenticationToken(
            UUID.randomUUID().toString(),
            null,
            List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_X")));
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThat(SecurityUtils.currentRoles()).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_X");
    assertThat(SecurityUtils.isAdmin()).isTrue();
  }

  @Test
  void isAdminFalseForNonAdminRoles() {
    var auth =
        new UsernamePasswordAuthenticationToken(
            UUID.randomUUID().toString(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThat(SecurityUtils.isAdmin()).isFalse();
  }
}
