/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.Objects;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.util.CommonUtils;

/**
 * PostgreSQL constraint drop SQL, including PostgreSQL 18 support for dropping
 * a constraint only from a partitioned parent table.
 */
public class Postgres180ConstraintDropBuilder {
	private final Dialect dialect;

	public Postgres180ConstraintDropBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	public String drop(Constraint constraint) {
		return drop(constraint, false, false, false);
	}

	public String dropOnly(Constraint constraint) {
		return drop(constraint, true, false, false);
	}

	public String drop(Constraint constraint, boolean only,
			boolean ifExists, boolean cascade) {
		Objects.requireNonNull(constraint, "constraint");
		if (constraint.getParent() == null
				|| constraint.getParent().getTable() == null) {
			throw new IllegalArgumentException(
					"Constraint must belong to a table.");
		}
		if (only && dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"Dropping a constraint ONLY from a partitioned table requires PostgreSQL 18 or later.");
		}
		StringBuilder builder = new StringBuilder("ALTER TABLE ");
		if (only) {
			builder.append("ONLY ");
		}
		if (!CommonUtils.isEmpty(constraint.getSchemaName())) {
			builder.append(dialect.quote(constraint.getSchemaName()))
					.append(".");
		}
		builder.append(dialect.quote(require(constraint.getTableName(),
				"tableName"))).append(" DROP CONSTRAINT ");
		if (ifExists) {
			builder.append("IF EXISTS ");
		}
		builder.append(dialect.quote(require(constraint.getName(),
				"constraintName")));
		if (cascade) {
			builder.append(" CASCADE");
		}
		return builder.toString();
	}

	private String require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
		return value;
	}
}
