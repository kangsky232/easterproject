package com.smoke.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 输出不含请求体、令牌和查询参数的 API 审计日志。
 */
@Component
@Slf4j
public class ApiAuditFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/")
                || "/api/health".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String principal = authentication == null ? "anonymous" : authentication.getName();
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("api_audit method={} path={} status={} durationMs={} principal={} client={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs,
                    principal,
                    request.getRemoteAddr());
        }
    }
}
