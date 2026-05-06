package com.inghubscl.ticketing.audit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

class AuditAspectTest {

  private final AuditService auditService = mock(AuditService.class);
  private final AuditAspect aspect = new AuditAspect(auditService);

  @Test
  void recordsResourceIdFromResultIdMethod() throws Throwable {
    UUID id = UUID.randomUUID();
    Result result = new Result(id);
    Audited audited = annotationFor("create", "event.create", "event", "");
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    when(pjp.proceed()).thenReturn(result);
    setSignature(pjp, "create");

    aspect.aroundAudited(pjp, audited);

    verify(auditService, times(1)).record("event.create", "event", id.toString());
  }

  @Test
  void recordsNullResourceIdWhenResultHasNoIdMethod() throws Throwable {
    Audited audited = annotationFor("noId", "user.login", "user", "");
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    when(pjp.proceed()).thenReturn(new Object());
    setSignature(pjp, "noId");

    aspect.aroundAudited(pjp, audited);

    verify(auditService).record("user.login", "user", null);
  }

  @Test
  void recordsNullWhenResultIsNull() throws Throwable {
    Audited audited = annotationFor("create", "event.create", "event", "");
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    when(pjp.proceed()).thenReturn(null);
    setSignature(pjp, "create");

    aspect.aroundAudited(pjp, audited);

    verify(auditService).record("event.create", "event", null);
  }

  @Test
  void doesNotRecordWhenJoinPointThrows() throws Throwable {
    Audited audited = annotationFor("create", "event.create", "event", "");
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    when(pjp.proceed()).thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> aspect.aroundAudited(pjp, audited))
        .isInstanceOf(RuntimeException.class);

    verify(auditService, never())
        .record(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void usesSpelExpressionToResolveResourceIdFromResult() throws Throwable {
    Result result = new Result(UUID.randomUUID());
    Audited audited = annotationFor("create", "event.create", "event", "#result.id");
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    when(pjp.proceed()).thenReturn(result);
    setSignature(pjp, "create");
    when(pjp.getTarget()).thenReturn(this);
    when(pjp.getArgs()).thenReturn(new Object[] {});

    aspect.aroundAudited(pjp, audited);

    verify(auditService).record("event.create", "event", result.id().toString());
  }

  @Test
  void fallsBackToNullWhenSpelExpressionFails() throws Throwable {
    Audited audited = annotationFor("create", "event.create", "event", "#unknown.field");
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    when(pjp.proceed()).thenReturn(new Result(UUID.randomUUID()));
    setSignature(pjp, "create");
    when(pjp.getTarget()).thenReturn(this);
    when(pjp.getArgs()).thenReturn(new Object[] {});

    aspect.aroundAudited(pjp, audited);

    verify(auditService).record("event.create", "event", null);
  }

  // --- helpers ---

  static record Result(UUID id) {}

  Object create() {
    return null;
  }

  Object noId() {
    return null;
  }

  private void setSignature(ProceedingJoinPoint pjp, String methodName) throws Exception {
    Method m = AuditAspectTest.class.getDeclaredMethod(methodName);
    MethodSignature sig = mock(MethodSignature.class);
    when(sig.getMethod()).thenReturn(m);
    when((Signature) pjp.getSignature()).thenReturn(sig);
  }

  private static Audited annotationFor(
      String unused, String action, String resourceType, String idExpression) {
    return new Audited() {
      @Override
      public Class<? extends java.lang.annotation.Annotation> annotationType() {
        return Audited.class;
      }

      @Override
      public String action() {
        return action;
      }

      @Override
      public String resourceType() {
        return resourceType;
      }

      @Override
      public String resourceIdExpression() {
        return idExpression;
      }
    };
  }
}
