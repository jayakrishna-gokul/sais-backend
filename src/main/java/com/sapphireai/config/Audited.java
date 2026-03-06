package com.sapphireai.config;

import com.sapphireai.model.entity.audit.AuditEvent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark any controller or method to be audited.
 *
 * Usage:
 *   @Audited(eventType = EventType.SEARCH, resourceType = "SEARCH")
 *   public ResponseEntity<SearchResponse> search(...) { }
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    AuditEvent.EventType eventType();
    String resourceType() default "";
}
