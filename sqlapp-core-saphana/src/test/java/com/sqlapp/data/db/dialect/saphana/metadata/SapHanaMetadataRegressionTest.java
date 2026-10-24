/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SapHanaMetadataRegressionTest {
	private static ExResultSet row(Map<String, String> values) {
		Map<String, String> normalized = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		normalized.putAll(values);
		return new ExResultSet(null) {
			private boolean wasNull;

			@Override
			public Object getObject(String key) {
				return normalized.get(key);
			}

			@Override
			public String getString(String key) throws SQLException {
				if (Set.of("MATCH_OPTION", "CHARACTER_SET_NAME", "COLLATION_NAME")
						.contains(key.toUpperCase(Locale.ROOT))) {
					throw new SQLException("Not a HANA catalog column: " + key);
				}
				return normalized.get(key);
			}

			@Override
			public long getLong(String key) {
				return Long.parseLong(normalized.getOrDefault(key, "1"));
			}

			@Override
			public int getInt(String key) {
				wasNull = !normalized.containsKey(key);
				return wasNull ? 0 : Integer.parseInt(normalized.get(key));
			}

			@Override
			public boolean wasNull() {
				return wasNull;
			}
		};
	}

	@Test
	void readsGeneratedColumnsAndBothIdentityModes() throws Exception {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.cloudDialect)) {
			var reader = new SapHanaColumnReader(dialect);
			for (String type : List.of("ALWAYS AS", "ALWAYS AS IDENTITY", "BY DEFAULT AS IDENTITY")) {
				var values = new HashMap<>(Map.of("COLUMN_NAME", "TOTAL", "TABLE_NAME", "ITEMS", "SCHEMA_NAME", "APP",
						"DATA_TYPE_NAME", "INTEGER", "IS_NULLABLE", "TRUE", "GENERATION_TYPE", type));
				values.put("GENERATED_ALWAYS_AS", "PRICE * QUANTITY");
				Column column = reader.createColumn(row(values));
				assertEquals("APP", column.getSchemaName());
				assertEquals("ITEMS", column.getTableName());
				String ddl = dialect.createSqlBuilder().definition(column).toString();
				if (type.equals("ALWAYS AS")) {
					assertFalse(column.isIdentity());
					assertTrue(column.isFormulaPersisted());
					assertTrue(ddl.contains("GENERATED ALWAYS AS (PRICE * QUANTITY)"), ddl);
				} else {
					assertTrue(column.isIdentity());
					assertTrue(ddl.contains("GENERATED " + type), ddl);
				}
			}
		}
	}

	@Test
	void doesNotModelTimestampDisplayWidthAsPrecision() throws Exception {
		var reader = new SapHanaColumnReader(DialectHolder.defaultDialect);
		var column = reader.createColumn(
				row(Map.of("COLUMN_NAME", "D", "DATA_TYPE_NAME", "TIMESTAMP", "LENGTH", "27", "IS_NULLABLE", "TRUE")));
		assertNull(column.getLength());
		assertFalse(
				DialectHolder.defaultDialect.createSqlBuilder().definition(column).toString().contains("TIMESTAMP("));
	}

	@Test
	void readsOptionalColumnLoadUnitWithoutRequiringNewCatalogFields() throws Exception {
		var reader = new SapHanaColumnReader(DialectHolder.defaultDialect);
		var values = new HashMap<>(Map.of("COLUMN_NAME", "ID", "DATA_TYPE_NAME", "INTEGER", "IS_NULLABLE", "TRUE"));
		assertNull(reader.createColumn(row(values)).getSpecifics().get("LOAD_UNIT"));
		values.put("LOAD_UNIT", "PAGE");
		assertEquals("PAGE", reader.createColumn(row(values)).getSpecifics().get("LOAD_UNIT"));
	}

	@Test
	void preservesFullExecutableViewRoutineAndTriggerDefinitions() throws Exception {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.cloudDialect)) {
			var registry = dialect.createSqlFactoryRegistry();
			String viewDdl = "CREATE VIEW \"Odd Schema\".V AS SELECT 1 AS N FROM DUMMY";
			Table view = new SapHanaViewReader(dialect)
					.createTable(row(Map.of("VIEW_NAME", "V", "SCHEMA_NAME", "Odd Schema", "DEFINITION", viewDdl)));
			assertEquals(viewDdl, registry.createSql(view, SqlType.CREATE).getFirst().getSqlText());
			String procedureDdl = "CREATE PROCEDURE \"Odd Schema\".P (OUT N INTEGER) LANGUAGE SQLSCRIPT AS BEGIN N = 1; END";
			Procedure procedure = new SapHanaProcedureReader(dialect).createProcedure(
					row(Map.of("PROCEDURE_NAME", "P", "SCHEMA_NAME", "Odd Schema", "DEFINITION", procedureDdl)));
			assertEquals(procedureDdl, registry.createSql(procedure, SqlType.CREATE).getFirst().getSqlText());
			String functionDdl = "CREATE FUNCTION \"Odd Schema\".F () RETURNS N INTEGER LANGUAGE SQLSCRIPT AS BEGIN N = 1; END";
			Function function = new SapHanaFunctionReader(dialect).createFunction(
					row(Map.of("FUNCTION_NAME", "F", "SCHEMA_NAME", "Odd Schema", "DEFINITION", functionDdl)));
			assertEquals(functionDdl, registry.createSql(function, SqlType.CREATE).getFirst().getSqlText());
			String triggerDdl = "CREATE TRIGGER \"Odd Schema\".TR AFTER INSERT ON \"Odd Schema\".T FOR EACH STATEMENT BEGIN END";
			Trigger trigger = new SapHanaTriggerReader(dialect).createTrigger(
					row(Map.of("TRIGGER_NAME", "TR", "SCHEMA_NAME", "Odd Schema", "DEFINITION", triggerDdl,
							"SUBJECT_TABLE_SCHEMA", "Odd Schema", "SUBJECT_TABLE_NAME", "T", "TRIGGERED_ACTION_LEVEL",
							"STATEMENT", "TRIGGER_EVENT", "INSERT", "TRIGGER_ACTION_TIME", "AFTER")));
			assertEquals("T", trigger.getTableName());
			assertEquals(triggerDdl, registry.createSql(trigger, SqlType.CREATE).getFirst().getSqlText());
		}
	}

	@Test
	void keepsTableScopedCompositeForeignKeysSeparateWithOneQuery() {
		var queries = new AtomicInteger();
		var reader = new SapHanaForeignKeyConstraintReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				try {
					for (String table : List.of("CHILD_A", "CHILD_B")) {
						for (String column : List.of("P1", "P2")) {
							handler.handleResultSetNext(row(Map.of("SCHEMA_NAME", "APP", "TABLE_NAME", table,
									"CONSTRAINT_NAME", "FK_PARENT", "COLUMN_NAME", column, "REFERENCED_SCHEMA_NAME",
									"REF", "REFERENCED_TABLE_NAME", "PARENT_" + table, "REFERENCED_COLUMN_NAME",
									"R" + column)));
						}
					}
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				return null;
			}
		};
		var constraints = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, queries.get());
		assertEquals(2, constraints.size());
		for (var constraint : constraints) {
			assertEquals(2, constraint.getColumns().size());
			assertEquals("P1", constraint.getColumns().get(0).getName());
			assertEquals("P2", constraint.getColumns().get(1).getName());
			assertEquals("PARENT_" + constraint.getTableName(), constraint.getRelatedTableName());
			assertEquals("REF", constraint.getRelatedTableSchemaName());
		}
	}

	@Test
	void triggerQueryFiltersTriggerNameSeparatelyFromSubjectTable() throws Exception {
		var reader = new SapHanaTriggerReader(DialectHolder.defaultDialect);
		var context = new ParametersContext();
		context.put("triggerName", "TR");
		var parameters = new com.sqlapp.jdbc.sql.SqlParameterCollection(DialectHolder.defaultDialect);
		reader.getSqlSqlNode(new ProductVersionInfo()).eval(context, parameters);
		assertTrue(parameters.getSql().contains("t.TRIGGER_NAME IN"), parameters.getSql());
		assertFalse(parameters.getSql().contains("t.SUBJECT_TABLE_NAME IN"), parameters.getSql());
		context.put("tableName", "T");
		parameters = new com.sqlapp.jdbc.sql.SqlParameterCollection(DialectHolder.defaultDialect);
		reader.getSqlSqlNode(new ProductVersionInfo()).eval(context, parameters);
		assertTrue(parameters.getSql().contains("t.SUBJECT_TABLE_NAME IN"), parameters.getSql());
	}

	@Test
	void readsProcedureArgumentsUsingOnlyHanaCatalogColumns() throws Exception {
		var reader = new SapHanaProcedureArgumentReader(DialectHolder.defaultDialect);
		var argument = reader.createNamedArgument(row(Map.of("PROCEDURE_NAME", "P", "SCHEMA_NAME", "APP",
				"PARAMETER_NAME", "N", "DATA_TYPE_NAME", "INTEGER", "IS_NULLABLE", "TRUE", "PARAMETER_TYPE", "INOUT")));
		assertEquals("N", argument.getName());
		assertEquals(com.sqlapp.jdbc.sql.ParameterDirection.Inout, argument.getDirection());
	}

	@Test
	void uniqueConstraintQueryHonorsConstraintNameFilter() {
		var reader = new SapHanaUniqueConstraintReader(DialectHolder.defaultDialect);
		var context = new ParametersContext();
		context.put("constraintName", "PK_ITEMS");
		var parameters = new com.sqlapp.jdbc.sql.SqlParameterCollection(DialectHolder.defaultDialect);
		reader.getSqlSqlNode(new ProductVersionInfo()).eval(context, parameters);
		assertTrue(parameters.getSql().contains("I.INDEX_NAME IN"), parameters.getSql());
	}
}
