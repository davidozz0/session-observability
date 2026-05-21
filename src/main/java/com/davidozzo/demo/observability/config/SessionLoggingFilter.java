package com.davidozzo.demo.observability.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1)
public class SessionLoggingFilter implements Filter {

    private static final String SESSION_HEADER = "X-Session-Id";
    private static final String START_PATH = "/api/session/start";

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
        } finally {
            MDC.clear();
        }
    }

    private String resolveSessionId(HttpServletRequest request) {
        if (START_PATH.equals(request.getRequestURI())) {
            return UUID.randomUUID().toString();
        }
        return request.getHeader(SESSION_HEADER);
    }
}
