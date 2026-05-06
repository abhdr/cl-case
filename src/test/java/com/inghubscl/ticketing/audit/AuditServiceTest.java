package com.inghubscl.ticketing.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditServiceTest {

  @Mock AuditLogRepository repository;
  @InjectMocks AuditService service;

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void recordsActorAndRequestMetadata() {
    UUID actorId = UUID.randomUUID();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                actorId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));

    var request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.10");
    request.addHeader("User-Agent", "TestAgent/1.0");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    service.record("event.create", "event", "abc-123");

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(repository).save(captor.capture());
    AuditLog saved = captor.getValue();
    assertThat(saved.getActorId()).isEqualTo(actorId);
    assertThat(saved.getAction()).isEqualTo("event.create");
    assertThat(saved.getResourceType()).isEqualTo("event");
    assertThat(saved.getResourceId()).isEqualTo("abc-123");
    assertThat(saved.getIp()).isEqualTo("203.0.113.10");
    assertThat(saved.getUserAgent()).isEqualTo("TestAgent/1.0");
  }

  @Test
  void prefersXForwardedForHeaderForIp() {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("10.0.0.1");
    request.addHeader("X-Forwarded-For", "198.51.100.42, 10.0.0.1");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    service.record("any", "any", null);

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getIp()).isEqualTo("198.51.100.42");
  }

  @Test
  void recordsAnonymousActorAsNull() {
    service.record("any", "any", "1");

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getActorId()).isNull();
  }

  @Test
  void swallowsRepositoryExceptionsInAsyncWrite() {
    when(repository.save(any(AuditLog.class))).thenThrow(new RuntimeException("DB down"));

    service.record("event.create", "event", "x");

    verify(repository, times(1)).save(any(AuditLog.class));
  }

  @Test
  void writesAuditEntityWhenEverythingIsNull() {
    service.record("a", "r", null);
    verify(repository, times(1)).save(any(AuditLog.class));
    verify(repository, never()).deleteAll();
  }
}
