package com.parking.common;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class TraceFilter extends OncePerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(TraceFilter.class);
  private final MeterRegistry metrics;

  public TraceFilter(MeterRegistry metrics) {
    this.metrics = metrics;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String id = request.getHeader("X-Request-Id");
    if (id == null || !id.matches("[a-zA-Z0-9_-]{1,64}")) id = UUID.randomUUID().toString();
    response.setHeader("X-Request-Id", id);
    response.setHeader("X-Content-Type-Options", "nosniff");
    MDC.put("requestId", id);
    long started = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      if (request.getRequestURI().startsWith("/api/")) {
        String route =
            java.util.Objects.toString(
                request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE), "unmatched");
        long elapsed = System.nanoTime() - started;
        Timer.builder("parking.api")
            .tags("route", request.getMethod() + " " + route, "status", "" + response.getStatus())
            .register(metrics)
            .record(elapsed, TimeUnit.NANOSECONDS);
        LOG.info(
            "api method={} route={} status={} durationMs={} requestId={}",
            request.getMethod(),
            route,
            response.getStatus(),
            elapsed / 1000000,
            id);
      }
      MDC.remove("requestId");
    }
  }
}
