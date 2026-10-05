package com.example.common.dto.response;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void testPageResponseBuilder() {
        List<String> items = List.of("item1", "item2");
        PageResponse<String> page = PageResponse.<String>builder()
                .content(items)
                .page(0)
                .size(10)
                .totalElements(2L)
                .totalPages(1)
                .build();

        assertThat(page.getContent()).containsExactly("item1", "item2");
        assertThat(page.getPage()).isZero();
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.getTotalElements()).isEqualTo(2L);
        assertThat(page.getTotalPages()).isEqualTo(1);
    }
}
