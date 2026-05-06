package com.inghubscl.ticketing.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {

  String action();

  String resourceType();

  /** SpEL expression evaluated against method args to produce resourceId; optional. */
  String resourceIdExpression() default "";
}
