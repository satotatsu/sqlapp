/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;

class SqliteAlterTableFactoryTest {
	@Test
	void insertingColumnsBeforeOrBetweenExistingColumnsFailsBeforeSqlIsReturned() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var original = table();
		original.getColumns().add(new Column("tail").setDataType(DataType.INT));
		for (int position : new int[] { 0, 1 }) {
			var target = original.clone();
			target.getColumns().add(position, new Column("inserted").setDataType(DataType.INT));
			var error = assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.diff(target), SqlType.ALTER));
			assertTrue(error.getMessage().contains("append"));
		}
	}

	@Test
	void multipleAppendedColumnsKeepModelOrderAndExistingData() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE other.t(value TEXT)");
			statement.execute("INSERT INTO other.t VALUES('kept')");
			var original = table();
			var target = original.clone();
			target.getColumns().add(new Column("first_added").setDataType(DataType.INT).setDefaultValue("7"));
			target.getColumns().add(new Column("second_added").setDataType(DataType.INT).setDefaultValue("9"));
			execute(statement, DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(original.diff(target),
					SqlType.ALTER));
			try (var result = statement.executeQuery("SELECT * FROM other.t")) {
				assertTrue(result.next());
				assertEquals("kept", result.getString(1));
				assertEquals("first_added", result.getMetaData().getColumnName(2));
				assertEquals(7, result.getInt(2));
				assertEquals("second_added", result.getMetaData().getColumnName(3));
				assertEquals(9, result.getInt(3));
			}
		}
	}

	@Test
	void appendingNotNullVirtualColumnsUsesExistingRowsAndPreservesTheFormula() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE other.t(value TEXT)");
			statement.execute("INSERT INTO other.t VALUES('kept')");
			var original = table();
			var target = original.clone();
			target.getColumns()
					.add(new Column("computed").setDataType(DataType.INT).setFormula("length(value)").setNotNull(true));
			execute(statement, DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(original.diff(target),
					SqlType.ALTER));
			try (var result = statement.executeQuery("SELECT value,computed FROM other.t")) {
				assertTrue(result.next());
				assertEquals("kept", result.getString(1));
				assertEquals(4, result.getInt(2));
			}
			statement.execute("UPDATE other.t SET value='longer'");
			try (var result = statement.executeQuery("SELECT computed FROM other.t")) {
				assertTrue(result.next());
				assertEquals(6, result.getInt(1));
			}
		}
	}

	@Test
	void virtualNotNullViolationsAreReportedByTheEngineWithoutAddingTheColumn() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE other.t(value TEXT)");
			statement.execute("INSERT INTO other.t VALUES(NULL)");
			var original = table();
			var target = original.clone();
			target.getColumns()
					.add(new Column("computed").setDataType(DataType.INT).setFormula("length(value)").setNotNull(true));
			var operations = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(original.diff(target),
					SqlType.ALTER);
			assertEquals(1, operations.size());
			assertThrows(java.sql.SQLException.class, () -> statement.execute(operations.get(0).getSqlText()));
			try (var result = statement.executeQuery("SELECT * FROM other.t")) {
				assertEquals(1, result.getMetaData().getColumnCount());
				assertTrue(result.next());
				assertNull(result.getString(1));
			}
		}
	}

	@Test
	void generatedAdditionsRetainVersionAndStoredColumnGuards() {
		var original = table();
		var target = original.clone();
		var generated = new Column("computed").setDataType(DataType.INT).setFormula("length(value)").setNotNull(true);
		target.getColumns().add(generated);
		var old = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect("SQLite", 3, 30, 0)
				.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> old.createSql(original.diff(target), SqlType.ALTER));
		generated.setFormulaPersisted(true);
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void droppingLeadingColumnPreservesFollowingColumnsAndTheirData() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(obsolete TEXT, value TEXT, tail TEXT)");
			statement.execute("INSERT INTO t VALUES ('removed','kept','last')");
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("main");
			reader.setObjectName("t");
			var original = reader.getAllFull(connection).get(0);
			var target = original.clone();
			target.getColumns().remove("obsolete");
			execute(statement, DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(original.diff(target),
					SqlType.ALTER));
			try (var rs = statement.executeQuery("SELECT * FROM t")) {
				assertTrue(rs.next());
				assertEquals(2, rs.getMetaData().getColumnCount());
				assertEquals("kept", rs.getString(1));
				assertEquals("last", rs.getString(2));
			}
		}
	}

	@Test
	void dropTargetsAttachedObjectsAndNeverUsesCascadeConstraints() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			for (String schema : List.of("main", "other")) {
				statement.execute("CREATE TABLE " + schema + ".t(value TEXT)");
				statement.execute("CREATE INDEX " + schema + ".ix ON t(value)");
				statement.execute("CREATE VIEW " + schema + ".v AS SELECT value FROM t");
				statement.execute("CREATE TRIGGER " + schema + ".tr AFTER INSERT ON t BEGIN SELECT 1; END");
			}
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			assertFalse(DialectHolder.defaultDialect.supportsDropCascade());
			execute(statement, registry.createSql(new Index("ix").setSchemaName("other"), SqlType.DROP));
			execute(statement,
					registry.createSql(new com.sqlapp.data.schemas.Trigger("tr").setSchemaName("other"), SqlType.DROP));
			execute(statement,
					registry.createSql(new com.sqlapp.data.schemas.View("v").setSchemaName("other"), SqlType.DROP));
			var operations = registry.createSql(table(), SqlType.DROP);
			assertTrue(operations.stream().noneMatch(op -> op.getSqlText().contains("CASCADE")));
			execute(statement, operations);
			for (String schema : List.of("main", "other")) {
				try (var rs = statement.executeQuery(
						"SELECT count(*) FROM " + schema + ".sqlite_master WHERE name IN ('t','ix','v','tr')")) {
					assertTrue(rs.next());
					assertEquals(schema.equals("main") ? 4 : 0, rs.getInt(1));
				}
			}
		}
	}

	@Test
	void standaloneConstraintChangesAndInternalIndexDropsFailClearly() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (var constraint : List.of(new UniqueConstraint("uq"),
				new com.sqlapp.data.schemas.CheckConstraint("ck", "value>0"))) {
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(constraint, SqlType.CREATE));
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(constraint, SqlType.DROP));
		}
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(new Index("sqlite_autoindex_t_1"), SqlType.DROP));
	}

	private Table table() {
		var table = new Table("t").setSchemaName("other");
		table.getColumns().add(new Column("value").setDataType(DataType.VARCHAR));
		return table;
	}

	private void execute(Statement statement, List<SqlOperation> operations) throws Exception {
		assertFalse(operations.isEmpty());
		for (var operation : operations)
			statement.execute(operation.getSqlText());
	}

	@Test
	void addDropAndExplicitRenamePreserveAttachedTableData() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE other.t(value TEXT)");
			statement.execute("INSERT INTO other.t VALUES ('kept')");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var original = table();
			var target = original.clone();
			target.getColumns().add(
					new Column("added").setDataType(DataType.VARCHAR).setDefaultValue("'default'").setNotNull(true));
			execute(statement, registry.createSql(original.diff(target), SqlType.ALTER));
			try (var rs = statement.executeQuery("SELECT value, added FROM other.t")) {
				assertTrue(rs.next());
				assertEquals("kept", rs.getString(1));
				assertEquals("default", rs.getString(2));
			}
			var factory = (SqliteAlterTableFactory) registry.getSqlFactory(target, SqlType.ALTER);
			execute(statement, factory.renameColumn(target, "added", "renamed"));
			target.getColumns().get("added").setName("renamed");
			original = target.clone();
			target.getColumns().remove("renamed");
			execute(statement, registry.createSql(original.diff(target), SqlType.ALTER));
			try (var rs = statement.executeQuery("SELECT value FROM other.t")) {
				assertTrue(rs.next());
				assertEquals("kept", rs.getString(1));
			}
			original = target.clone();
			target.setName("renamed_table");
			execute(statement, registry.createSql(original.diff(target), SqlType.ALTER));
			assertTrue(statement.executeQuery("SELECT value FROM other.renamed_table").next());
		}
	}

	@Test
	void indexChangesUseDialectFactoriesAndAttachedNames() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE other.t(value TEXT)");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var original = table();
			var target = original.clone();
			var index = new Index("ix");
			index.getColumns().add("value");
			index.setWhere("value IS NOT NULL");
			target.getIndexes().add(index);
			execute(statement, registry.createSql(original.diff(target), SqlType.ALTER));
			original = target.clone();
			target.getIndexes().remove("ix");
			execute(statement, registry.createSql(original.diff(target), SqlType.ALTER));
			try (var rs = statement
					.executeQuery("SELECT count(*) FROM other.sqlite_master WHERE type='index' AND name='ix'")) {
				assertTrue(rs.next());
				assertEquals(0, rs.getInt(1));
			}
		}
	}

	@Test
	void unsupportedChangesFailWithoutReturningPartialSql() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var original = table();
		var changedType = original.clone();
		changedType.getColumns().get("value").setDataType(DataType.INT);
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(original.diff(changedType), SqlType.ALTER));
		var addedConstraint = original.clone();
		var unique = new UniqueConstraint("uq");
		unique.getColumns().add("value");
		addedConstraint.getConstraints().add(unique);
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(original.diff(addedConstraint), SqlType.ALTER));
		for (Column column : List.of(new Column("bad").setDataType(DataType.INT).setIdentity(true),
				new Column("bad").setDataType(DataType.VARCHAR).setNotNull(true),
				new Column("bad").setDataType(DataType.VARCHAR).setDefaultValue("CURRENT_TIMESTAMP"),
				new Column("bad").setDataType(DataType.VARCHAR).setDefaultValue("(upper('x'))"))) {
			var target = original.clone();
			target.getColumns().add(column);
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.diff(target), SqlType.ALTER));
		}
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original, SqlType.ALTER));
	}
}
