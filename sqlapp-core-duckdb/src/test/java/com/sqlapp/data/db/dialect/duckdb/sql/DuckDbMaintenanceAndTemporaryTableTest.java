/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

/**
 * Temporary tables, ANALYZE, CHECKPOINT and structured macro/type DDL, executed
 * against an in-memory DuckDB.
 */
class DuckDbMaintenanceAndTemporaryTableTest {

	private static Table table() {
		final Schema schema = new Schema("s1");
		final Table table = new Table("work");
		schema.getTables().add(table);
		table.getColumns().add(new Column("id").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("parent_id").setDataType(DataType.INT));
		table.getColumns().add(new Column("v").setDataType(DataType.VARCHAR));
		table.setPrimaryKey("pk_work", table.getColumns().get("id"));
		table.getConstraints().addForeignKeyConstraint("fk_self", table.getColumns().get("parent_id"),
				table.getColumns().get("id"));
		table.getIndexes().add("ix_work_v", table.getColumns().get("v"));
		table.setRemarks("work table");
		return table;
	}

	@Test
	void temporaryTablesAreUnqualifiedAndExecutable() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
				Statement statement = connection.createStatement()) {
			final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			final SqlFactoryRegistry registry = dialect.createSqlFactoryRegistry();
			registry.getTableOptions().setTemporaryTableNamePrefix(t -> "tmp_");
			final List<SqlOperation> create = registry.createSql(table(), SqlType.CREATE_TEMPORARY);
			assertEquals(SqlType.CREATE_TEMPORARY, create.get(0).getSqlType());
			final String ddl = create.get(0).getSqlText();
			assertTrue(ddl.matches("(?s)CREATE TEMPORARY TABLE (IF NOT EXISTS )?tmp_work\\s.*"), ddl);
			assertTrue(ddl.contains("REFERENCES tmp_work"), ddl);
			for (final SqlOperation operation : create) {
				assertTrue(!operation.getSqlText().contains("s1."), operation.getSqlText());
				statement.execute(operation.getSqlText());
			}
			statement.execute("INSERT INTO tmp_work VALUES (1, NULL, 'a')");
			final List<SqlOperation> truncate = registry.createSql(table(), SqlType.TRUNCATE_TEMPORARY);
			assertEquals("TRUNCATE TABLE tmp_work", truncate.get(0).getSqlText());
			statement.execute(truncate.get(0).getSqlText());
			try (ResultSet rs = statement.executeQuery(
					"SELECT count(*), (SELECT count(*) FROM duckdb_tables() WHERE database_name = 'temp') FROM tmp_work")) {
				assertTrue(rs.next());
				assertEquals(0, rs.getInt(1));
				assertEquals(1, rs.getInt(2));
			}
		}
	}

	@Test
	void temporaryTablesRejectForeignKeysToOtherTables() {
		final SqlFactoryRegistry registry = DuckDbSqlFactoryTestSupport.registry();
		final Table table = table();
		final Table other = new Table("other");
		table.getSchema().getTables().add(other);
		other.getColumns().add(new Column("id").setDataType(DataType.INT));
		table.getConstraints().getForeignKeyConstraints().clear();
		table.getConstraints().addForeignKeyConstraint("fk_other", table.getColumns().get("parent_id"),
				other.getColumns().get("id"));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE_TEMPORARY));
	}

	@Test
	void analyzeAndCheckpointAreExecutable() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
				Statement statement = connection.createStatement()) {
			final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			final SqlFactoryRegistry registry = dialect.createSqlFactoryRegistry();
			statement.execute("CREATE SCHEMA s1");
			statement.execute("CREATE TABLE s1.work (id INTEGER)");
			final List<SqlOperation> analyze = registry.createSql(table(), SqlType.ANALYZE);
			assertEquals("ANALYZE s1.work", analyze.get(0).getSqlText());
			statement.execute(analyze.get(0).getSqlText());
			final List<SqlOperation> checkpoint = registry.createSql(new Catalog("memory"), SqlType.DEFRAG);
			assertEquals("CHECKPOINT memory", checkpoint.get(0).getSqlText());
			statement.execute(checkpoint.get(0).getSqlText());
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Catalog(), SqlType.DEFRAG));
		}
	}

	@Test
	void structuredMacroAndEnumDdl() {
		final SqlFactoryRegistry registry = DuckDbSqlFactoryTestSupport.registry();
		final Function macro = new Function("plus").setSchemaName("s1");
		macro.getArguments().add(new com.sqlapp.data.schemas.NamedArgument("a"));
		macro.getArguments().add(new com.sqlapp.data.schemas.NamedArgument("order"));
		macro.setStatement(List.of("a + \"order\""));
		macro.setRemarks("it's");
		final List<SqlOperation> create = registry.createSql(macro, SqlType.CREATE);
		assertEquals("CREATE MACRO s1.plus(a, \"order\") AS a + \"order\"", create.get(0).getSqlText());
		assertEquals("COMMENT ON MACRO s1.plus IS 'it''s'", create.get(1).getSqlText());
		macro.setFunctionType(FunctionType.Table);
		assertEquals("DROP MACRO TABLE s1.plus", registry.createSql(macro, SqlType.DROP).get(0).getSqlText());
		macro.setFunctionType(FunctionType.Aggregate);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(macro, SqlType.CREATE));

		final Domain type = new Domain("mood").setSchemaName("s1");
		type.setDataType(DataType.ENUM);
		type.getValues().add("a");
		type.getValues().add("it's");
		assertEquals("CREATE TYPE s1.mood AS ENUM ('a', 'it''s')",
				registry.createSql(type, SqlType.CREATE).get(0).getSqlText());
		assertEquals("DROP TYPE s1.mood", registry.createSql(type, SqlType.DROP).get(0).getSqlText());
		final Domain domain = new Domain("d").setSchemaName("s1");
		domain.setDataType(DataType.INT);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(domain, SqlType.CREATE));
	}
}
