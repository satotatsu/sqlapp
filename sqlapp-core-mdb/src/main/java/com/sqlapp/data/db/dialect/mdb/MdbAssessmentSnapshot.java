/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import java.util.List;

import com.sqlapp.data.schemas.Schema;

/** Local Schema plus source assets that cannot be represented as portable Schema objects.
 * Connection strings, query text and row values are deliberately not collected. */
public record MdbAssessmentSnapshot(Schema schema, List<String> linkedTables,
		List<SavedQuery> savedQueries, boolean relationshipsCollected) {
	public MdbAssessmentSnapshot {
		linkedTables = List.copyOf(linkedTables);
		savedQueries = List.copyOf(savedQueries);
	}

	public record SavedQuery(String name, String type, boolean hidden, boolean parameterized) { }
}
