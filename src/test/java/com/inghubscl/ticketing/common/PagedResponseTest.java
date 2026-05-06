package com.inghubscl.ticketing.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PagedResponseTest {

  @Test
  void mapsSpringPageToWrapper() {
    var page = new PageImpl<>(List.of("a", "b", "c"), PageRequest.of(1, 3), 7);

    PagedResponse<String> response = PagedResponse.from(page);

    assertThat(response.content()).containsExactly("a", "b", "c");
    assertThat(response.page()).isEqualTo(1);
    assertThat(response.size()).isEqualTo(3);
    assertThat(response.totalElements()).isEqualTo(7);
    assertThat(response.totalPages()).isEqualTo(3);
    assertThat(response.last()).isFalse();
  }

  @Test
  void emptyPageIsLast() {
    PagedResponse<String> response =
        PagedResponse.from(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

    assertThat(response.last()).isTrue();
    assertThat(response.totalElements()).isZero();
  }
}
