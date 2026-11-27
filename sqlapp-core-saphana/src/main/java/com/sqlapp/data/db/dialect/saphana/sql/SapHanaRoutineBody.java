/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;

final class SapHanaRoutineBody {
	private SapHanaRoutineBody() {
	}

	static void append(final String statement, final SapHanaSqlBuilder builder) {
		if (statement == null || statement.isBlank()) {
			throw new IllegalArgumentException(
					"SAP HANA routine requires a SQLScript statement or complete definition");
		}
		if (statement.stripLeading().matches("(?is)BEGIN\\b.*")) {
			builder.lineBreak()._add("LANGUAGE SQLSCRIPT AS");
		} else if (!statement.stripLeading().matches("(?is)(LANGUAGE|SQL\\s+SECURITY|DEFAULT\\s+SCHEMA|AS)\\b.*")) {
			throw new IllegalArgumentException(
					"SAP HANA routine statement must begin with BEGIN or complete LANGUAGE/AS clauses");
		}
		builder.lineBreak()._add(statement);
	}
}
