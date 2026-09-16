/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Small deterministic SQL fixture; SQL uses the standard sqlapp comment template. */
public record MigrationTransformationTest(String id, String sql, Map<String, Object> parameters,
		List<List<String>> expectedRows, boolean ordered) {

	public MigrationTransformationTest {
		if (id == null || id.isBlank() || sql == null || sql.isBlank()) {
			throw new IllegalArgumentException("Transformation test id and sql are required");
		}
		parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
		final List<List<String>> rows = new ArrayList<>();
		if (expectedRows != null) {
			for (final List<String> row : expectedRows) {
				rows.add(Collections.unmodifiableList(new ArrayList<>(row)));
			}
		}
		expectedRows = List.copyOf(rows);
	}
}
