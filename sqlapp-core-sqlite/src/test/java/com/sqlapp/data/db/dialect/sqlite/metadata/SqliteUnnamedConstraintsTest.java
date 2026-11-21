/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.dialect.sqlite.SqliteSpecifics;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;

/**
 * Names generated for unnamed constraints are marked, and SQLite recreation
 * omits them so the original DDL is reproduced.
 */
class SqliteUnnamedConstraintsTest {
	private static java.util.Set<String> named(com.sqlapp.data.schemas.ConstraintCollection constraints) {
		return constraints.stream().filter(c -> !SqliteSpecifics.isUnnamed(c) && c.getName() != null)
				.map(c -> c.getName()).collect(java.util.stream.Collectors.toSet());
	}

	@Test
	void generatedNamesAreMarkedAndNotWrittenBack() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE parent(id INTEGER PRIMARY KEY)");
			statement.execute(
					"""
							CREATE TABLE child(id INTEGER PRIMARY KEY, code TEXT UNIQUE, parent_id INTEGER REFERENCES parent(id),
							 other_id INTEGER, CHECK (id > 0),
							 CONSTRAINT uq_named UNIQUE (code, other_id), CONSTRAINT ck_named CHECK (other_id <> 0),
							 CONSTRAINT fk_named FOREIGN KEY (other_id) REFERENCES parent(id))
							""");
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("main");
			reader.setObjectName("child");
			Table child = reader.getAllFull(connection).get(0);
			var constraints = child.getConstraints();
			int unnamed = 0;
			for (var constraint : constraints) {
				boolean named = constraint.getName() != null && constraint.getName().endsWith("_named");
				if (constraint instanceof com.sqlapp.data.schemas.UniqueConstraint unique && unique.isPrimaryKey())
					continue;
				assertEquals(!named, SqliteSpecifics.isUnnamed(constraint), constraint.getName());
				if (!named)
					unnamed++;
			}
			assertEquals(3, unnamed);
			Table restored = SchemaUtils.readXml(new java.io.StringReader(child.asXml()));
			statement.execute("DROP TABLE child");
			for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored,
					SqlType.CREATE))
				statement.execute(operation.getSqlText());
			try (var rs = statement.executeQuery("SELECT sql FROM sqlite_master WHERE name='child'")) {
				assertTrue(rs.next());
				String sql = rs.getString(1);
				assertFalse(sql.contains("sqlite_unique_"), sql);
				assertFalse(sql.contains("sqlite_check_"), sql);
				assertFalse(sql.contains("sqlite_fk_"), sql);
				assertTrue(sql.contains("uq_named") && sql.contains("ck_named") && sql.contains("fk_named"), sql);
			}
			// Reading the recreated table yields the same named and unnamed constraints.
			Table reread = reader.getAllFull(connection).get(0);
			assertEquals(constraints.size(), reread.getConstraints().size());
			assertEquals(3, reread.getConstraints().stream().filter(SqliteSpecifics::isUnnamed).count());
			assertEquals(named(constraints), named(reread.getConstraints()));
		}
	}
}
