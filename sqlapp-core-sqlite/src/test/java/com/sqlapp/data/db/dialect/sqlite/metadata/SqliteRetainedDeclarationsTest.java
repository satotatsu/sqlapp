/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Table;

/**
 * Declarations kept from the catalog reproduce the original DDL only while they
 * still describe the Schema model; a changed model wins.
 */
class SqliteRetainedDeclarationsTest {
	@Test
	void declaredTypeIsKeptOnlyWhileItMatchesTheModel() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id INT, name VARCHAR(30), amount custom_money, untyped)");
			var table = read(connection, "t");
			assertEquals("INT", type(table, "id"));
			assertEquals("VARCHAR(30)", type(table, "name"));
			assertEquals("custom_money", type(table, "amount"));
			assertEquals("", type(table, "untyped"));
			table.getColumns().get("id").setDataType(DataType.VARCHAR);
			table.getColumns().get("name").setLength(50L);
			table.getColumns().get("amount").setDataType(DataType.DOUBLE);
			table.getColumns().get("untyped").setDataType(DataType.BIGINT);
			assertEquals("TEXT", type(table, "id"));
			assertEquals("TEXT", type(table, "name"));
			assertEquals("REAL", type(table, "amount"));
			assertEquals("INTEGER", type(table, "untyped"));
			// The retained declarations themselves are unchanged in the model.
			assertEquals("INT", table.getColumns().get("id").getSpecifics().get("sqlite.declaredType"));
		}
	}

	@Test
	void indexKeysAreKeptOnlyWhileTheyMatchTheModel() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(a TEXT, b TEXT)");
			statement.execute("CREATE INDEX ix ON t(a COLLATE NOCASE DESC, lower(b))");
			var table = read(connection, "t");
			var index = table.getIndexes().get("ix");
			assertEquals("a COLLATE NOCASE DESC, lower(b)", SqliteDdl.retainedIndexKeys(index));
			assertTrue(create(index).contains("(a COLLATE NOCASE DESC, lower(b))"));
			index.getColumns().clear();
			index.getColumns().add("b", Order.Asc);
			assertNull(SqliteDdl.retainedIndexKeys(index));
			String ddl = create(index);
			assertFalse(ddl.contains("NOCASE"), ddl);
			statement.execute("DROP INDEX ix");
			statement.execute(ddl);
			try (var rs = statement.executeQuery("SELECT name FROM pragma_index_info('ix')")) {
				assertTrue(rs.next());
				assertEquals("b", rs.getString(1));
				assertFalse(rs.next());
			}
			// A reversed order is a model change as well.
			var reread = read(connection, "t").getIndexes().get("ix");
			// The generated DDL quotes the name; the retained keys keep that spelling.
			assertEquals(reread.getSpecifics().get(SqliteDdl.INDEX_KEYS), SqliteDdl.retainedIndexKeys(reread));
			assertEquals("b", reread.getColumns().get(0).getName());
			reread.getColumns().get(0).setOrder(Order.Desc);
			assertNull(SqliteDdl.retainedIndexKeys(reread));
		}
	}

	private static String type(Table table, String column) {
		return DialectHolder.defaultDialect.createSqlBuilder().addTypeDefinition(table.getColumns().get(column))
				.toString();
	}

	private static String create(com.sqlapp.data.schemas.Index index) {
		return DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(index, SqlType.CREATE).get(0)
				.getSqlText();
	}

	private static Table read(Connection connection, String name) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName("main");
		reader.setObjectName(name);
		return reader.getAllFull(connection).get(0);
	}
}
