package com.inghubscl.ticketing.audit;

import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {

  private final AuditService auditService;
  private final ExpressionParser parser = new SpelExpressionParser();
  private final DefaultParameterNameDiscoverer paramDiscoverer =
      new DefaultParameterNameDiscoverer();

  public AuditAspect(AuditService auditService) {
    this.auditService = auditService;
  }

  @Around("@annotation(audited)")
  public Object aroundAudited(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
    Object result = joinPoint.proceed(); // only audit on success
    String resourceId = resolveResourceId(joinPoint, audited, result);
    auditService.record(audited.action(), audited.resourceType(), resourceId);
    return result;
  }

  private String resolveResourceId(ProceedingJoinPoint joinPoint, Audited audited, Object result) {
    String expr = audited.resourceIdExpression();
    if (expr == null || expr.isBlank()) {
      if (result == null) {
        return null;
      }
      try {
        Method idGetter = result.getClass().getMethod("id");
        Object id = idGetter.invoke(result);
        return id == null ? null : id.toString();
      } catch (ReflectiveOperationException ignored) {
        return null;
      }
    }
    try {
      MethodSignature sig = (MethodSignature) joinPoint.getSignature();
      Method method = sig.getMethod();
      MethodBasedEvaluationContext ctx =
          new MethodBasedEvaluationContext(
              joinPoint.getTarget(), method, joinPoint.getArgs(), paramDiscoverer);
      ctx.setVariable("result", result);
      Object value = parser.parseExpression(expr).getValue(ctx);
      return value == null ? null : value.toString();
    } catch (Exception ex) {
      return null;
    }
  }
}
