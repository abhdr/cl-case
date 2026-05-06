package com.inghubscl.ticketing.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CachedBodyHttpServletRequestTest {

  @Test
  void cachesBodyAndAllowsMultipleReads() throws IOException {
    var raw = new MockHttpServletRequest();
    raw.setContent("{\"seats\":2}".getBytes(StandardCharsets.UTF_8));
    raw.setContentType("application/json");

    var wrapper = new CachedBodyHttpServletRequest(raw);

    assertThat(new String(wrapper.getCachedBody(), StandardCharsets.UTF_8))
        .isEqualTo("{\"seats\":2}");

    String firstRead = new String(wrapper.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String secondRead = new String(wrapper.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(firstRead).isEqualTo(secondRead).isEqualTo("{\"seats\":2}");
  }

  @Test
  void readerReturnsSameBody() throws IOException {
    var raw = new MockHttpServletRequest();
    raw.setContent("payload".getBytes(StandardCharsets.UTF_8));

    var wrapper = new CachedBodyHttpServletRequest(raw);

    assertThat(wrapper.getReader().readLine()).isEqualTo("payload");
  }

  @Test
  void inputStreamReportsReadyAndFinishesAtEnd() throws IOException {
    var raw = new MockHttpServletRequest();
    raw.setContent("ab".getBytes(StandardCharsets.UTF_8));
    var wrapper = new CachedBodyHttpServletRequest(raw);

    var stream = wrapper.getInputStream();
    assertThat(stream.isReady()).isTrue();
    assertThat(stream.isFinished()).isFalse();
    stream.read();
    stream.read();
    assertThat(stream.isFinished()).isTrue();
  }
}
