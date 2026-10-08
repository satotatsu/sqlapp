/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.metadata;

import java.util.Locale;

import com.sqlapp.data.db.dialect.postgres.sql.PostgresConstraintOptions;
import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.data.db.dialect.postgres.sql.Postgres180CreateForeignKeyConstraintFactory;
import com.sqlapp.data.db.dialect.postgres.sql.Postgres180CreateUniqueConstraintFactory;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.UniqueConstraint;
import java.util.regex.Pattern;

final class PostgresTemporalConstraintMetadata {
	private PostgresTemporalConstraintMetadata() {
	}

	static void apply(UniqueConstraint constraint, String definition) {
		if (matches(definition, "\\bWITHOUT\\s+OVERLAPS\\s*\\)")) {
			constraint.getSpecifics().put(Postgres180CreateUniqueConstraintFactory.WITHOUT_OVERLAPS, "true");
		}
	}

	static void apply(ForeignKeyConstraint constraint, String definition) {
		if (matches(definition, "[,(]\\s*PERIOD\\s+[_a-zA-Z]")) {
			constraint.getSpecifics().put(Postgres180CreateForeignKeyConstraintFactory.PERIOD, "true");
		}
		applyEnforcement(constraint, definition);
	}

	static void apply(CheckConstraint constraint, String definition) {
		applyEnforcement(constraint, definition);
	}

	private static void applyEnforcement(Constraint constraint, String definition) {
		if (definition != null && definition.toUpperCase(Locale.ROOT).trim()
				.matches("(?s).*\\sNOT VALID(?:\\s+NOT ENFORCED)?")) {
			constraint.getSpecifics().put(PostgresConstraintOptions.NOT_VALID, "true");
		}
		if (matches(definition, "\\sNOT ENFORCED(?:\\s+NOT VALID)?\\s*$")) {
			constraint.getSpecifics().put(
					com.sqlapp.data.db.dialect.postgres.sql.Postgres180CreateCheckConstraintFactory.NOT_ENFORCED,
					"true");
		}
	}

	private static boolean matches(String definition, String expression) {
		if (definition == null) return false;
		// pg_get_constraintdef quotes identifiers and string literals. Mask both
		// before looking for syntax, retaining a placeholder for quoted columns.
		String syntax = definition.replaceAll("'(''|[^'])*'|\"(\"\"|[^\"])*\"", "_quoted_");
		return Pattern.compile(expression, Pattern.CASE_INSENSITIVE).matcher(syntax).find();
	}
}
