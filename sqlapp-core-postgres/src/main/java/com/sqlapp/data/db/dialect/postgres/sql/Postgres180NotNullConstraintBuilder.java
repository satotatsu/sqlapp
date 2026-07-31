/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.Objects;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.schemas.NotNullConstraint;
import com.sqlapp.util.CommonUtils;

/**
 * PostgreSQL 18 state changes for named NOT NULL constraints.
 */
public class Postgres180NotNullConstraintBuilder {
	private final Dialect dialect;

	public Postgres180NotNullConstraintBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	public String setNotValid(NotNullConstraint constraint) {
		return alterConstraint(constraint, "NOT VALID");
	}

	public String validate(NotNullConstraint constraint) {
		checkVersion();
		return alterTable(constraint) + " VALIDATE CONSTRAINT "
				+ dialect.quote(require(constraint.getName(),
						"constraintName"));
	}

	public String setNoInherit(NotNullConstraint constraint, boolean value) {
		return alterConstraint(constraint,
				value ? "NO INHERIT" : "INHERIT");
	}

	private String alterConstraint(NotNullConstraint constraint,
			String action) {
		checkVersion();
		return alterTable(constraint) + " ALTER CONSTRAINT "
				+ dialect.quote(require(constraint.getName(),
						"constraintName"))
				+ " " + action;
	}

	private String alterTable(NotNullConstraint constraint) {
		Objects.requireNonNull(constraint, "constraint");
		if (constraint.getTable() == null) {
			throw new IllegalArgumentException(
					"NotNullConstraint must belong to a table.");
		}
		StringBuilder builder = new StringBuilder("ALTER TABLE ");
		if (!CommonUtils.isEmpty(constraint.getSchemaName())) {
			builder.append(dialect.quote(constraint.getSchemaName()))
					.append(".");
		}
		return builder.append(dialect.quote(require(
				constraint.getTableName(), "tableName"))).toString();
	}

	private void checkVersion() {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"Named NOT NULL constraint alteration requires PostgreSQL 18 or later.");
		}
	}

	private String require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
		return value;
	}
}
