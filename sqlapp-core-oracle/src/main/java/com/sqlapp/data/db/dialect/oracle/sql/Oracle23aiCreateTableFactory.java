/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/**
 * Oracle Database 23ai CREATE TABLE extensions.
 */
public class Oracle23aiCreateTableFactory extends OracleCreateTableFactory {

	@Override
	protected void addCreateObject(final Table table,
			final OracleSqlBuilder builder) {
		builder.create().table()
				.ifNotExists(this.getOptions().isCreateIfNotExists()).space();
		builder.name(table, this.getOptions().isDecorateSchemaName());
	}

	@Override
	protected void addOtherDefinitions(final Table table,
			final List<SqlOperation> result) {
		super.addOtherDefinitions(table, result);
		if (!OracleAnnotationUtils.getAnnotations(table).isEmpty()) {
			final OracleSqlBuilder builder = createSqlBuilder();
			builder.alter().table().space()
					.name(table, this.getOptions().isDecorateSchemaName());
			OracleAnnotationUtils.addAnnotations(builder, table);
			addSql(result, builder, SqlType.ALTER, table);
		}
		for (Column column : table.getColumns()) {
			if (OracleAnnotationUtils.getAnnotations(column).isEmpty()) {
				continue;
			}
			final OracleSqlBuilder builder = createSqlBuilder();
			builder.alter().table().space()
					.name(table, this.getOptions().isDecorateSchemaName())
					.space()._add("MODIFY").space().name(column);
			OracleAnnotationUtils.addAnnotations(builder, column);
			addSql(result, builder, SqlType.ALTER, column);
		}
	}
}
