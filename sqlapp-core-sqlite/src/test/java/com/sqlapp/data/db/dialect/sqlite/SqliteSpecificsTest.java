/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/**
 * Table option keys use the sqlite. prefix; earlier unprefixed keys still work.
 */
class SqliteSpecificsTest {
	@Test
	void legacyOptionKeysStillCreateAndCompare() throws Exception {
		var dialect = DialectHolder.defaultDialect;
		var registry = dialect.createSqlFactoryRegistry();
		var table = new Table("t").setSchemaName("main");
		table.getColumns().add(new Column("id").setDataType(DataType.BIGINT));
		table.getColumns().add(new Column("value").setDataType(DataType.VARCHAR));
		table.getSpecifics().put(SqliteSpecifics.LEGACY_STRICT, "true");
		assertTrue(SqliteSpecifics.isStrict(table));
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			for (var operation : registry.createSql(table, SqlType.CREATE))
				statement.execute(operation.getSqlText());
			var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("main");
			reader.setObjectName("t");
			Table original = reader.getAllFull(connection).get(0);
			assertEquals("true", original.getSpecifics().get(SqliteSpecifics.STRICT));
			assertNull(original.getSpecifics().get(SqliteSpecifics.LEGACY_STRICT));
			// A model saved with the earlier key differs only in spelling, not in meaning.
			Table target = original.clone();
			target.getSpecifics().remove(SqliteSpecifics.STRICT);
			target.getSpecifics().put(SqliteSpecifics.LEGACY_STRICT, "true");
			target.getColumns().add(new Column("note").setDataType(DataType.VARCHAR));
			var operations = registry.createSql(original.diff(target), SqlType.ALTER);
			assertEquals(1, operations.size(), operations.toString());
			for (var operation : operations)
				statement.execute(operation.getSqlText());
			try (var rs = statement.executeQuery("SELECT count(*) FROM pragma_table_info('t')")) {
				assertTrue(rs.next());
				assertEquals(3, rs.getInt(1));
			}
		}
		var withoutRowid = new Table("w");
		withoutRowid.getSpecifics().put(SqliteSpecifics.LEGACY_WITHOUT_ROWID, "true");
		assertTrue(SqliteSpecifics.isWithoutRowid(withoutRowid));
		SqliteSpecifics.migrateLegacyKeys(withoutRowid);
		assertEquals("true", withoutRowid.getSpecifics().get(SqliteSpecifics.WITHOUT_ROWID));
		assertNull(withoutRowid.getSpecifics().get(SqliteSpecifics.LEGACY_WITHOUT_ROWID));
	}

	@Test
	void ddlIsTransactional() throws Exception {
		assertTrue(DialectHolder.defaultDialect.isDdlRollbackable());
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			connection.setAutoCommit(false);
			statement.execute("CREATE TABLE rolled_back(id INTEGER)");
			connection.rollback();
			try (var rs = statement.executeQuery("SELECT count(*) FROM sqlite_master WHERE name='rolled_back'")) {
				assertTrue(rs.next());
				assertEquals(0, rs.getInt(1));
			}
		}
	}
}
