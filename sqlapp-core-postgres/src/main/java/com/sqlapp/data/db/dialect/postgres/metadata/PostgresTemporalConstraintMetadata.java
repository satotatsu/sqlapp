/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.metadata;

import java.util.Locale;

import com.sqlapp.data.db.dialect.postgres.sql.Postgres180CreateForeignKeyConstraintFactory;
import com.sqlapp.data.db.dialect.postgres.sql.Postgres180CreateUniqueConstraintFactory;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.CommonUtils;

final class PostgresTemporalConstraintMetadata {
	private PostgresTemporalConstraintMetadata() {
	}

	static void apply(UniqueConstraint constraint, String definition) {
		if (contains(definition, "WITHOUT OVERLAPS")) {
			constraint.getSpecifics().put(
					Postgres180CreateUniqueConstraintFactory.WITHOUT_OVERLAPS,
					"true");
		}
	}

	static void apply(ForeignKeyConstraint constraint, String definition) {
		if (contains(definition, "PERIOD")) {
			constraint.getSpecifics().put(
					Postgres180CreateForeignKeyConstraintFactory.PERIOD, "true");
		}
		applyEnforcement(constraint, definition);
	}

	static void apply(CheckConstraint constraint, String definition) {
		applyEnforcement(constraint, definition);
	}

	private static void applyEnforcement(
			com.sqlapp.data.schemas.Constraint constraint, String definition) {
		if (contains(definition, "NOT ENFORCED")) {
			constraint.getSpecifics().put(
					com.sqlapp.data.db.dialect.postgres.sql.Postgres180CreateCheckConstraintFactory.NOT_ENFORCED,
					"true");
		}
	}

	private static boolean contains(String value, String token) {
		return !CommonUtils.isEmpty(value)
				&& value.toUpperCase(Locale.ROOT).contains(token);
	}
}
