/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.bulk.SqliteBulkUpsertProvider;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class SqliteVersionCapabilitiesTest {
	private Sqlite version(int minor, Integer patch) {
		return (Sqlite) DialectResolver.getInstance().getDialect("SQLite", 3, minor, patch);
	}

	private void boundary(int minor, int patch, Predicate<Sqlite> capability) {
		assertFalse(capability.test(patch == 0 ? version(minor - 1, 99) : version(minor, patch - 1)));
		assertTrue(capability.test(version(minor, patch)));
		assertTrue(capability.test(version(99, 0)));
	}

	@Test
	void featureBoundariesAreResolvedThroughThePublicResolver() {
		boundary(7, 6, Sqlite::supportsWalCheckpointModes);
		boundary(8, 8, Sqlite::supportsWalCheckpointTruncate);
		assertFalse(version(7, null).supportsWalCheckpointModes());
		assertFalse(version(8, null).supportsWalCheckpointTruncate());
		boundary(7, 16, Sqlite::supportsForeignKeyCheck);
		assertFalse(version(7, null).supportsForeignKeyCheck());
		assertSame(DialectHolder.sqlite3716, version(7, 16));
		assertSame(DialectHolder.sqlite376, version(7, 15));
		assertFalse(version(7, 16).supportsPartialIndexes());
		boundary(8, 0, Sqlite::supportsPartialIndexes);
		boundary(8, 2, Sqlite::supportsWithoutRowid);
		boundary(8, 3, Sqlite::supportsWith);
		boundary(8, 3, Sqlite::supportsWithRecursive);
		boundary(9, 0, Sqlite::supportsViewColumnList);
		boundary(9, 0, Sqlite::supportsExpressionIndexes);
		boundary(15, 0, Sqlite::supportsRowValueComparison);
		boundary(15, 0, Sqlite::supportsRowValueComparisonIn);
		boundary(24, 0, Sqlite::supportsUpsert);
		boundary(25, 0, Sqlite::supportsRenameColumn);
		boundary(27, 0, Sqlite::supportsVacuumInto);
		boundary(31, 0, Sqlite::supportsGeneratedColumns);
		boundary(31, 0, Sqlite::supportsColumnFormula);
		boundary(35, 0, Sqlite::supportsDropColumn);
		boundary(35, 0, Sqlite::supportsInsertReturningResultSet);
		boundary(37, 0, Sqlite::supportsStrictTables);
		assertEquals(999, version(31, 0).getMaxStatementParameterCount());
		assertEquals(32766, version(32, 0).getMaxStatementParameterCount());
		assertFalse(version(8, null).supportsWithoutRowid());
		assertTrue(DialectHolder.defaultDialect instanceof SqliteVersions.Sqlite337);
	}

	@Test
	void structuredDdlRejectsFeaturesUnavailableOnTheTarget() {
		var table = new Table("t");
		table.getColumns().add(new Column("value").setDataType(DataType.INT));
		table.getSpecifics().put("strict", "true");
		assertThrows(IllegalArgumentException.class,
				() -> version(36, 0).createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
		assertFalse(version(37, 0).createSqlFactoryRegistry().createSql(table, SqlType.CREATE).isEmpty());
		table.getSpecifics().remove("strict");
		table.getColumns().add(new Column("computed").setDataType(DataType.INT).setFormula("value + 1"));
		assertThrows(IllegalArgumentException.class,
				() -> version(30, 0).createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
		assertFalse(version(31, 0).createSqlFactoryRegistry().createSql(table, SqlType.CREATE).isEmpty());
		var provider = new SqliteBulkUpsertProvider();
		assertFalse(provider.supports(version(23, 0)));
		assertThrows(IllegalArgumentException.class, () -> provider.create(version(23, 0)));
		assertTrue(provider.supports(version(24, 0)));
		assertNotNull(provider.create(version(24, 0)));
	}

	@Test
	void alterIndexesAndReturningHonorVersionGuards() {
		var table = new Table("t");
		var column = new Column("value").setDataType(DataType.INT);
		table.getColumns().add(column);
		var oldRegistry = version(24, 0).createSqlFactoryRegistry();
		var alter = (com.sqlapp.data.db.dialect.sqlite.sql.SqliteAlterTableFactory) oldRegistry.getSqlFactory(table,
				SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> alter.renameColumn(table, "value", "renamed"));
		var target = table.clone();
		target.getColumns().clear();
		assertThrows(IllegalArgumentException.class,
				() -> version(34, 0).createSqlFactoryRegistry().createSql(table.diff(target), SqlType.ALTER));
		assertThrows(IllegalArgumentException.class,
				() -> version(34, 0).handleInsertReturningSql(table, column, "INSERT INTO t DEFAULT VALUES"));
		assertTrue(version(35, 0).handleInsertReturningSql(table, column, "INSERT INTO t DEFAULT VALUES")
				.contains("RETURNING"));
		var index = new com.sqlapp.data.schemas.Index("ix");
		table.getIndexes().add(index);
		index.getColumns().add(column);
		index.setWhere("value > 0");
		assertThrows(IllegalArgumentException.class,
				() -> version(7, 0).createSqlFactoryRegistry().createSql(index, SqlType.CREATE));
		assertFalse(version(8, 0).createSqlFactoryRegistry().createSql(index, SqlType.CREATE).isEmpty());
		index.setWhere(null);
		index.getSpecifics().put("sqlite.index.keys", "abs(value)");
		assertThrows(IllegalArgumentException.class,
				() -> version(8, 3).createSqlFactoryRegistry().createSql(index, SqlType.CREATE));
		assertFalse(version(9, 0).createSqlFactoryRegistry().createSql(index, SqlType.CREATE).isEmpty());
		assertFalse(com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl
				.hasIndexExpressions("[name(with)] COLLATE NOCASE DESC"));
	}

	@Test
	void oldTargetViewsRetainExecutableDefinitionsAndColumnNames() throws Exception {
		var dialect = version(8, 3);
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE VIEW v AS SELECT 7 AS preserved_name");
			var reader = dialect.getCatalogReader().getSchemaReader().getViewReader();
			reader.setSchemaName("main");
			reader.setObjectName("v");
			var view = reader.getAllFull(connection).get(0);
			assertFalse(view.getDefinition().isEmpty());
			assertTrue(view.getStatement().isEmpty());
			statement.execute("DROP VIEW v");
			for (var operation : dialect.createSqlFactoryRegistry().createSql(view, SqlType.CREATE))
				statement.execute(operation.getSqlText());
			try (var result = statement.executeQuery("SELECT preserved_name FROM v")) {
				assertTrue(result.next());
				assertEquals(7, result.getInt(1));
			}
		}
	}

	@Test
	void dateTimeExpressionsAndPagingMatchNativeSqliteSyntax() throws Exception {
		var dialect = version(37, 0);
		assertFalse(dialect.supportsTop());
		assertTrue(dialect.supportsLimit());
		assertTrue(dialect.supportsLimitOffset());
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement();
				var result = statement.executeQuery("SELECT " + dialect.getCurrentDateFunction() + ", "
						+ dialect.getCurrentTimeFunction() + ", " + dialect.getCurrentTimestampFunction() + ", "
						+ dialect.getCurrentDateTimeFunction() + " LIMIT 1 OFFSET 0")) {
			assertTrue(result.next());
			for (int i = 1; i <= 4; i++)
				assertNotNull(result.getString(i));
		}
	}
}
