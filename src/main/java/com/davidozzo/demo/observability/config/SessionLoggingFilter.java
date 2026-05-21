package com.davidozzo.demo.observability.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class SessionLoggingFilter implements Filter {

    private static final String SESSION_HEADER = "X-Session-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        try {
            String sessionId = extractSessionId(request);
            if (sessionId != null && !sessionId.isBlank()) {
                MDC.put("sessionId", sessionId);
            }
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String extractSessionId(ServletRequest request) {
        if (request instanceof HttpServletRequest httpRequest) {
            return httpRequest.getHeader(SESSION_HEADER);
        }
        return null;
    }
}
