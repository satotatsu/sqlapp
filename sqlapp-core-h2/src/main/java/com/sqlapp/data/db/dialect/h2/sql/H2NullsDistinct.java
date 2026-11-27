/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.h2.H2_220;

/**
 * Resolves the {@code h2.nullsDistinct} specific shared by UNIQUE constraints
 * and indexes.
 */
final class H2NullsDistinct {
	static final String KEY = "h2.nullsDistinct";

	private H2NullsDistinct() {
	}

	/**
	 * Returns the native clause, or null when nothing needs to be emitted. Before
	 * H2 2.2 only the implicit {@code YES} behavior exists, so it is accepted
	 * without a clause and other policies are rejected.
	 */
	static String clause(Dialect dialect, String policy) {
		if (policy == null) {
			return null;
		}
		String clause = switch (policy) {
		case "YES" -> "NULLS DISTINCT";
		case "NO" -> "NULLS NOT DISTINCT";
		case "ALL" -> "NULLS ALL DISTINCT";
		default -> throw new IllegalArgumentException(KEY + " must be YES, NO, or ALL: " + policy);
		};
		if (dialect instanceof H2_220) {
			return clause;
		}
		if ("YES".equals(policy)) {
			return null;
		}
		throw new IllegalArgumentException(KEY + "=" + policy + " requires H2 2.2 or later");
	}
}
