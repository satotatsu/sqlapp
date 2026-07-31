/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.Objects;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.util.CommonUtils;

/**
 * Version-aware PostgreSQL array function SQL builder.
 */
public class PostgresArrayBuilder {
	private final Dialect dialect;

	public PostgresArrayBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	public String arrayReverse(String arrayExpression) {
		checkVersion();
		require(arrayExpression);
		return "array_reverse(" + arrayExpression + ")";
	}

	public String arraySort(String arrayExpression) {
		checkVersion();
		require(arrayExpression);
		return "array_sort(" + arrayExpression + ")";
	}

	public String arraySort(String arrayExpression, boolean descending) {
		checkVersion();
		require(arrayExpression);
		return "array_sort(" + arrayExpression + ", " + descending + ")";
	}

	public String arraySort(String arrayExpression, boolean descending,
			boolean nullsFirst) {
		checkVersion();
		require(arrayExpression);
		return "array_sort(" + arrayExpression + ", " + descending + ", "
				+ nullsFirst + ")";
	}

	private void checkVersion() {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"array_sort and array_reverse require PostgreSQL 18 or later.");
		}
	}

	private void require(String value) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(
					"arrayExpression must not be empty.");
		}
	}
}
