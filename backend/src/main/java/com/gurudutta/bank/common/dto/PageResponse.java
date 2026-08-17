package com.gurudutta.bank.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * A stable pagination envelope.
 *
 * <p>Spring's {@code Page} serialises with an unstable, implementation-detail-heavy JSON shape, so
 * we map to this instead and keep the API contract under our control.
 */
public record PageResponse<T>(List<T> content,
                              int page,
                              int size,
                              long totalElements,
                              int totalPages,
                              boolean first,
                              boolean last) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
