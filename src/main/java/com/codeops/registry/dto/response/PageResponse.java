package com.codeops.registry.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages,
                              boolean isLast) {

    /**
     * Creates a {@link PageResponse} from a Spring Data {@link Page}.
     *
     * @param page the Spring Data page
     * @param <T>  the element type
     * @return the page response wrapper
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
