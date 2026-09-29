package com.prescriptionscanner.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Pagination envelope: { ok, items, pagination } */
public record PageResponse<T>(
		boolean ok,
		List<T> items,
		Pagination pagination) {

	public record Pagination(
			int page,
			int limit,
			long total,
			int totalPages,
			boolean hasNext,
			boolean hasPrev) {
	}

	public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
		List<T> items = page.getContent().stream().map(mapper).toList();
		return new PageResponse<>(
				true,
				items,
				new Pagination(
						page.getNumber() + 1,
						page.getSize(),
						page.getTotalElements(),
						Math.max(1, page.getTotalPages()),
						page.hasNext(),
						page.hasPrevious()));
	}
}
