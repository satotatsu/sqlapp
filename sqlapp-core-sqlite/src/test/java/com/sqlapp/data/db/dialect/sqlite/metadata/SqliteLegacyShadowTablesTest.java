/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.DriverManager;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;

/**
 * Before 3.37 the shadow tables of built-in virtual-table modules are derived
 * from the module, so both version paths read the same tables.
 */
class SqliteLegacyShadowTablesTest {
	@Test
	void shadowTablesOfBuiltInModulesAreNotReadAsTables() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE VIRTUAL TABLE docs USING fts5(body)");
			statement.execute("CREATE VIRTUAL TABLE boxes USING rtree(id, minX, maxX)");
			statement.execute("CREATE TABLE docs_extra(x)");
			statement.execute("CREATE TABLE plain(x)");
			var context = new ParametersContext();
			context.put("schemaName", "main");
			var reader = (SqliteTableReader) DialectHolder.defaultDialect.getCatalogReader().getSchemaReader()
					.getTableReader();
			for (int minor : List.of(36, 37)) {
				var names = reader
						.doGetAll(connection, context,
								new ProductVersionInfo().setMajorVersion(3).setMinorVersion(minor))
						.stream().map(Table::getName).sorted().toList();
				assertEquals(List.of("boxes", "docs", "docs_extra", "plain"), names, "3." + minor);
			}
		}
	}
}
