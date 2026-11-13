/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.duckdb.DuckDb;

/**
 * Shared checks for DuckDB metadata readers.
 */
final class DuckDbMetadataSupport {

	private DuckDbMetadataSupport() {
	}

	/**
	 * Fails clearly when constraint metadata cannot be read reliably. DuckDB 1.0
	 * does not expose constraint names in {@code duckdb_constraints()} and its
	 * {@code information_schema.referential_constraints} returns duplicated rows.
	 *
	 * @param dialect dialect resolved for the connection
	 */
	static void requireConstraintMetadata(final Dialect dialect) {
		if (!(dialect instanceof DuckDb duckDb) || !duckDb.supportsConstraintMetadata()) {
			throw new UnsupportedOperationException(
					"Reading DuckDB constraint metadata requires DuckDB 1.1 or later (resolved dialect: " + dialect
							+ ")");
		}
	}
}
