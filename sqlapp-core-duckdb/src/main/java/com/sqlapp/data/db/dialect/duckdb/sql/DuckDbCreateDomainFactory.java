/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import java.util.List;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateDomainFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Domain;

/**
 * {@code CREATE TYPE ... AS ENUM} for DuckDB. DuckDB has no domains, so only
 * ENUM domains are accepted.
 */
public class DuckDbCreateDomainFactory extends AbstractCreateDomainFactory<DuckDbSqlBuilder> {

	@Override
	protected void addCreateObject(final Domain obj, final DuckDbSqlBuilder builder) {
		if (obj.getDataType() != DataType.ENUM) {
			throw new IllegalArgumentException("DuckDB supports only ENUM types, not domains: " + obj.getName());
		}
		if (obj.getValues().isEmpty()) {
			throw new IllegalArgumentException("DuckDB ENUM type requires at least one value: " + obj.getName());
		}
		if (obj.getDefaultValue() != null || obj.isNotNull() || obj.getCheck() != null || !obj.getChecks().isEmpty()) {
			throw new IllegalArgumentException(
					"DuckDB ENUM types support neither defaults, NOT NULL nor checks: " + obj.getName());
		}
		builder.create().space()._add("TYPE").space().name(obj, getOptions().isDecorateSchemaName()).space()
				._add("AS ENUM (");
		boolean first = true;
		for (final String value : obj.getValues()) {
			if (!first) {
				builder._add(", ");
			}
			builder.sqlChar(value);
			first = false;
		}
		builder._add(")");
	}

	@Override
	protected void addOptions(final Domain obj, final List<SqlOperation> sqlList) {
		if (obj.getRemarks() != null) {
			DuckDbSqlFactories.addComment(this, obj, sqlList);
		}
	}
}
