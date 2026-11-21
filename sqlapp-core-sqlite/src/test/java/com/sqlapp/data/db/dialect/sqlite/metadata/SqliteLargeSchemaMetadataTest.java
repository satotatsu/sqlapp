/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.schemas.Table;

/**
 * SQLite before 3.32 accepts at most 999 bound parameters, so table names are
 * bound in bounded chunks while metadata stays set-based.
 */
class SqliteLargeSchemaMetadataTest {
	private static final int TABLES = SqliteMetadataQueries.MAX_NAMES_PER_QUERY * 2 + 100;

	@Test
	void readsEveryTableWithBoundedParameterCounts() throws Exception {
		try (var raw = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = raw.createStatement()) {
			raw.setAutoCommit(false);
			for (int i = 0; i < TABLES; i++) {
				String name = String.format("t%04d", i);
				statement.execute("CREATE TABLE " + name + "(id INTEGER PRIMARY KEY, code TEXT UNIQUE,"
						+ " parent_id INTEGER REFERENCES " + name + "(id), CHECK(id > 0)) STRICT");
				statement.execute("CREATE INDEX ix_" + name + " ON " + name + "(parent_id)");
			}
			raw.commit();
			raw.setAutoCommit(true);
			List<String> prepared = new ArrayList<>();
			Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { Connection.class }, (proxy, method, args) -> {
						if (method.getName().equals("prepareStatement"))
							prepared.add((String) args[0]);
						try {
							return method.invoke(raw, args);
						} catch (InvocationTargetException e) {
							throw e.getCause();
						}
					});
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("main");
			List<Table> tables = reader.getAllFull(connection);
			assertEquals(TABLES, tables.size());
			for (Table table : tables) {
				assertEquals(3, table.getColumns().size(), table.getName());
				assertEquals(1, table.getIndexes().size(), table.getName());
				assertEquals("true", table.getSpecifics().get("strict"), table.getName());
				assertTrue(table.getColumns().get("id").isIdentity(), table.getName());
				assertEquals(2, table.getConstraints().getUniqueConstraints().size(), table.getName());
				assertEquals(1, table.getConstraints().getCheckConstraints().size(), table.getName());
				assertEquals(1, table.getConstraints().getForeignKeyConstraints().size(), table.getName());
			}
			for (String sql : prepared) {
				long parameters = sql.chars().filter(c -> c == '?').count();
				assertTrue(parameters <= 999, parameters + " parameters: " + sql.substring(0, 80));
			}
			// Set-based: a few queries per metadata kind, never one per table.
			assertTrue(prepared.size() < 40, prepared.size() + " prepared statements");
		}
	}
}
