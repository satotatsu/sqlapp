/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration.assessment;

import java.util.List;
import java.util.Objects;

import com.sqlapp.data.schemas.Schema;

/** Canonical source metadata and collection evidence, including explicit coverage.
 * Native source details may be retained in Schema specifics. Assessors must not
 * mutate the supplied schemas or follow lazy row iterators without authorization. */
public record MigrationAssessmentSource(List<Schema> schemas, MigrationAssessment assessment,
		boolean dataScanned, boolean relationshipsCollected) {
	public MigrationAssessmentSource {
		schemas = List.copyOf(schemas);
		Objects.requireNonNull(assessment, "assessment");
		if (schemas.isEmpty() || schemas.getFirst().getProductName() == null
				|| schemas.getFirst().getProductName().isBlank()) {
			throw new IllegalArgumentException("Assessment source must contain schemas with a source product");
		}
		final String product = schemas.getFirst().getProductName();
		if (schemas.stream().anyMatch(schema -> !product.equals(schema.getProductName()))) {
			throw new IllegalArgumentException("Assessment source schemas must have the same source product");
		}
	}

	public String sourceProduct() { return schemas.getFirst().getProductName(); }
}
