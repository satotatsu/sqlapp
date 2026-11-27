/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropDomainFactory;
import com.sqlapp.data.schemas.Domain;

/**
 * {@code DROP TYPE} for DuckDB ENUM types.
 */
public class DuckDbDropDomainFactory extends AbstractDropDomainFactory<DuckDbSqlBuilder> {

	@Override
	protected void addDropObject(final Domain obj, final DuckDbSqlBuilder builder) {
		if (obj.getDataType() != null && obj.getDataType() != DataType.ENUM) {
			throw new IllegalArgumentException("DuckDB supports only ENUM types, not domains: " + obj.getName());
		}
		builder.drop().space()._add("TYPE").space().name(obj, getOptions().isDecorateSchemaName());
	}
}
