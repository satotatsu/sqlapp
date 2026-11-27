/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractNamedObject;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;

/**
 * {@code COMMENT ON} for DuckDB tables, views, columns, indexes, sequences,
 * macros and ENUM types. A null remark removes the comment. DuckDB cannot
 * comment on schemas.
 */
public class DuckDbSetCommentFactory extends AbstractSqlFactory<AbstractNamedObject<?>, DuckDbSqlBuilder> {

	@Override
	public List<SqlOperation> createSql(final AbstractNamedObject<?> object) {
		final String kind;
		if (object instanceof Column) {
			kind = "COLUMN";
		} else if (object instanceof View) {
			kind = "VIEW";
		} else if (object instanceof Table) {
			kind = "TABLE";
		} else if (object instanceof Index) {
			kind = "INDEX";
		} else if (object instanceof Sequence) {
			kind = "SEQUENCE";
		} else if (object instanceof Function function) {
			kind = function.getFunctionType() == FunctionType.Table ? "MACRO TABLE" : "MACRO";
		} else if (object instanceof Domain domain && domain.getDataType() == DataType.ENUM) {
			kind = "TYPE";
		} else {
			throw new IllegalArgumentException(
					"DuckDB comments are not supported for " + object.getClass().getSimpleName());
		}
		requireName(object.getName());
		final DuckDbSqlBuilder builder = createSqlBuilder(getDialect());
		builder.comment().on().space()._add(kind).space();
		if (object instanceof Column column) {
			requireName(column.getTableName());
			if (getOptions().isDecorateSchemaName() && column.getSchemaName() != null) {
				builder.name(column.getSchemaName())._add(".");
			}
			builder.name(column.getTableName())._add(".").name(column.getName());
		} else if (object instanceof Index index) {
			builder.name(index, getOptions().isDecorateSchemaName());
		} else if (object instanceof AbstractSchemaObject<?> named) {
			builder.name(named, getOptions().isDecorateSchemaName());
		} else {
			builder.name(object.getName());
		}
		builder.space()._add("IS ");
		if (object.getRemarks() == null) {
			builder._add("NULL");
		} else {
			builder.sqlChar(object.getRemarks());
		}
		return List.of(new SqlOperation(builder.toString(), SqlType.SET_COMMENT, object));
	}

	private static void requireName(final String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("DuckDB COMMENT requires nonempty object identifiers");
		}
	}
}
