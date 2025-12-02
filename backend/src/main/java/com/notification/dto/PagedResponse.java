package com.notification.dto;

import java.util.List;

/**
 * DTO для постраничного ответа.
 */
public record PagedResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
