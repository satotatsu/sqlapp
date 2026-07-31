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
 * SQL builder for checks provided by the PostgreSQL {@code amcheck} extension.
 */
public class PostgresAmcheckBuilder {
	private final Dialect dialect;

	public PostgresAmcheckBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	/**
	 * Creates a PostgreSQL 18 GIN structural consistency check.
	 *
	 * @param indexName the index name
	 * @return SELECT statement invoking {@code gin_index_check}
	 */
	public String ginIndexCheck(String indexName) {
		return ginIndexCheck(null, indexName);
	}

	/**
	 * Creates a PostgreSQL 18 GIN structural consistency check.
	 *
	 * @param schemaName the optional schema name
	 * @param indexName the index name
	 * @return SELECT statement invoking {@code gin_index_check}
	 */
	public String ginIndexCheck(String schemaName, String indexName) {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"gin_index_check requires PostgreSQL 18 or later.");
		}
		require(indexName, "indexName");
		StringBuilder relation = new StringBuilder();
		if (!CommonUtils.isEmpty(schemaName)) {
			relation.append(dialect.quote(schemaName)).append(".");
		}
		relation.append(dialect.quote(indexName));
		return "SELECT gin_index_check("
				+ sqlString(relation.toString()) + "::regclass)";
	}

	private String sqlString(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	private void require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
	}
}
