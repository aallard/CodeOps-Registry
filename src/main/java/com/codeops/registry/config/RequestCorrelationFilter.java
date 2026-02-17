package com.codeops.registry.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that establishes MDC correlation context for every HTTP request.
 *
 * <p>Runs at the highest precedence (before security filters) to ensure that all
 * downstream log statements include a correlation ID. If the client sends an
 * {@code X-Correlation-ID} header, that value is reused; otherwise a new UUID
 * is generated. The correlation ID is also added to the response header so the
 * client can correlate requests with server logs.</p>
 *
 * <p>MDC keys populated:</p>
 * <ul>
 *   <li>{@code correlationId} — unique request identifier</li>
 *   <li>{@code requestPath} — the request URI</li>
 *   <li>{@code requestMethod} — the HTTP method</li>
 * </ul>
 *
 * <p>All MDC entries are cleared in a {@code finally} block to prevent
 * thread-pool contamination.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    /** HTTP header name used for request correlation. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /** MDC key for the correlation ID. */
    public static final String MDC_CORRELATION_ID = "correlationId";

    /** MDC key for the request path. */
    public static final String MDC_REQUEST_PATH = "requestPath";

    /** MDC key for the HTTP method. */
    public static final String MDC_REQUEST_METHOD = "requestMethod";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            MDC.put(MDC_CORRELATION_ID, correlationId);
            MDC.put(MDC_REQUEST_PATH, request.getRequestURI());
            MDC.put(MDC_REQUEST_METHOD, request.getMethod());

            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_REQUEST_PATH);
            MDC.remove(MDC_REQUEST_METHOD);
            MDC.remove("userId");
            MDC.remove("teamId");
        }
    }
}
