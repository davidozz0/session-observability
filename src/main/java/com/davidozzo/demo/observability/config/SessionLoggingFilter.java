package com.davidozzo.demo.observability.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Component
@Order(1)
public class SessionLoggingFilter implements Filter {

    private static final String SESSION_HEADER = "X-Session-Id";
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/session/start",
            "/api/session/list",
            "/h2-console"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        try {
            if (request instanceof HttpServletRequest httpRequest) {
                String sessionId = resolveSessionId(httpRequest);
                if (sessionId != null) {
                    MDC.put("sessionId", sessionId);
                }
            }
            chain.doFilter(request, response);
        } catch (IllegalArgumentException e) {
            if (response instanceof HttpServletResponse httpResponse) {
                httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            }
        } finally {
            MDC.clear();
        }
    }

    private String resolveSessionId(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (path.equals("/api/session/start")) {
            return UUID.randomUUID().toString();
        }

        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            return null;
        }

        String header = request.getHeader(SESSION_HEADER);
        if (header == null || header.isBlank()) {
            throw new IllegalArgumentException("Header mancante: " + SESSION_HEADER);
        }
        return header;
    }
}
