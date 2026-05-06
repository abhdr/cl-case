package com.inghubscl.ticketing.common;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

  private final TraceIdFilter filter = new TraceIdFilter();

  @Test
  void generatesNewTraceIdWhenHeaderMissing() throws Exception {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> assertThat(MDC.get(TraceIdFilter.TRACE_ID_KEY)).isNotBlank();

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isNotBlank();
    assertThat(MDC.get(TraceIdFilter.TRACE_ID_KEY)).isNull();
  }

  @Test
  void reusesIncomingHeader() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "fixed-id-123");
    var response = new MockHttpServletResponse();
    FilterChain chain =
        (req, res) -> assertThat(MDC.get(TraceIdFilter.TRACE_ID_KEY)).isEqualTo("fixed-id-123");

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("fixed-id-123");
  }

  @Test
  void clearsMdcAfterRequest() throws Exception {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(MDC.get(TraceIdFilter.TRACE_ID_KEY)).isNull();
  }
}
