package com.sapphireai.config;

import com.sapphireai.model.entity.audit.AuditEvent;
import com.sapphireai.service.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Instant;

/**
 * AOP Aspect — intercepts controller methods annotated with @Audited.
 * Logs who called what, result, and timing.
 *
 * No controller code changes needed — just add @Audited to any method.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditService auditService;

    @Value("${sapphire.audit.enabled:true}")
    private boolean auditEnabled;

    // FIX 1: pointcut now references the correct package — com.sapphireai.config.Audited
    @Around("@within(com.sapphireai.config.Audited) || @annotation(com.sapphireai.config.Audited)")
    public Object auditMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!auditEnabled) {
            return joinPoint.proceed();
        }

        Audited audited = resolveAnnotation(joinPoint);
        Instant start = Instant.now();
        AuditEvent.EventResult result = AuditEvent.EventResult.SUCCESS;
        String details = null;

        try {
            Object returnValue = joinPoint.proceed();
            return returnValue;
        } catch (Exception e) {
            result = AuditEvent.EventResult.FAILURE;
            details = e.getMessage();
            throw e;
        } finally {
            try {
                AuditEvent event = buildAuditEvent(audited, joinPoint, result, details, start);
                auditService.log(event);
            } catch (Exception e) {
                // Audit logging must NEVER break business logic
                log.error("Failed to write audit log", e);
            }
        }
    }

    private AuditEvent buildAuditEvent(Audited audited, ProceedingJoinPoint joinPoint,
                                       AuditEvent.EventResult result, String details, Instant start) {

        HttpServletRequest request = getCurrentRequest();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        String userEmail = "anonymous";
        String orgId = null;

        if (auth != null && auth.isAuthenticated()) {
            userEmail = auth.getName();
        }

        String action = joinPoint.getSignature().getDeclaringTypeName()
                + "." + joinPoint.getSignature().getName();

        if (request != null) {
            action = request.getMethod() + " " + request.getRequestURI();
        }

        // FIX 3: null-safe — audited can be null if resolution failed
        AuditEvent.EventType eventType = (audited != null)
                ? audited.eventType()
                : AuditEvent.EventType.SECURITY;

        String resourceType = (audited != null)
                ? audited.resourceType()
                : "UNKNOWN";

        return AuditEvent.builder()
                .userEmail(userEmail)
                .organizationId(orgId)
                .eventType(eventType)
                .resourceType(resourceType)
                .action(action)
                .ipAddress(request != null ? getClientIp(request) : null)
                .result(result)
                .details(details)
                .timestamp(start)
                .build();
    }

    /**
     * FIX 2: Proper annotation resolution using java.lang.reflect.
     *
     * The old version had three fatal problems:
     *   - joinPoint.getSignature().getDeclaringType() returns AspectJ's Class<?>, not java.lang.reflect.Class
     *     so calling .getMethod() on it doesn't exist
     *   - org.aspectj.weave.loadtime.definition.Definition is an AspectJ weaver internal —
     *     it is not on the runtime classpath
     *   - instanceof ... .class is not valid Java syntax
     *
     * Solution: get the actual java.lang.Class from the AspectJ declaring type,
     * then use standard reflection to find the method and read the annotation.
     */
    private Audited resolveAnnotation(ProceedingJoinPoint joinPoint) {
        try {
            // Step 1: get the real java.lang.Class (not AspectJ's wrapper)
            Class<?> declaringClass = joinPoint.getSignature().getDeclaringType();
            String methodName = joinPoint.getSignature().getName();

            // Step 2: try exact match first — build param types from the actual args
            Object[] args = joinPoint.getArgs();
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = (args[i] != null) ? args[i].getClass() : Object.class;
            }

            Method method = null;
            try {
                method = declaringClass.getMethod(methodName, paramTypes);
            } catch (NoSuchMethodException e) {
                // Exact match failed (e.g. interface types vs impl types in args).
                // Scan all public methods by name instead.
                for (Method m : declaringClass.getMethods()) {
                    if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                        method = m;
                        break;
                    }
                }
            }

            // Step 3: check method-level annotation first
            if (method != null) {
                Audited ann = method.getAnnotation(Audited.class);
                if (ann != null) {
                    return ann;
                }
            }

            // Step 4: fall back to class-level annotation
            return declaringClass.getAnnotation(Audited.class);

        } catch (Exception e) {
            log.warn("Could not resolve @Audited annotation on {}.{}",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(), e);
            return null;
        }
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        return xfHeader != null ? xfHeader.split(",")[0] : request.getRemoteAddr();
    }
}