/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SapHanaRoutineMetadataTest {
	private static ExResultSet row(String routine, String argument, String column, String type) {
		return new ExResultSet(null) {
			@Override public String getString(String key) {
				return switch (key.toUpperCase(Locale.ROOT)) {
				case "SCHEMA_NAME" -> "APP";
				case "FUNCTION_NAME", "PROCEDURE_NAME" -> routine;
				case "PARAMETER_NAME" -> argument;
				case "DATA_TYPE_NAME" -> "TABLE_TYPE";
				case "PARAMETER_TYPE" -> "IN";
				case "IS_NULLABLE", "TABLE_COLUMN_IS_NULLABLE" -> "TRUE";
				case "TABLE_COLUMN_NAME" -> column;
				case "TABLE_COLUMN_DATA_TYPE_NAME" -> type;
				default -> null;
				};
			}
			@Override public long getLong(String key) { return key.equals("TABLE_COLUMN_LENGTH") ? 20 : 0; }
			@Override public int getInt(String key) { return 0; }
			@Override public boolean wasNull() { return true; }
		};
	}

	@Test
	void groupsTableTypesInOneQuery() {
		var queries = new AtomicInteger();
		var reader = new SapHanaTypeReader(DialectHolder.defaultDialect) {
			@Override protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context, ResultSetNextHandler handler) {
				queries.incrementAndGet();
				try {
					for (String type : List.of("T1", "T2")) {
						for (String column : List.of("A B", "LABEL")) {
							var source = row(type, null, column, "INTEGER");
							handler.handleResultSetNext(new ExResultSet(null) {
								@Override public String getString(String key) throws SQLException { return key.equalsIgnoreCase("TYPE_NAME") ? type : source.getString(key); }
								@Override public int getInt(String key) { return 0; }
								@Override public long getLong(String key) { return 0; }
								@Override public boolean wasNull() { return true; }
							});
						}
					}
				} catch (SQLException e) { throw new RuntimeException(e); }
				return null;
			}
		};
		var types = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, queries.get());
		assertEquals(2, types.size());
		assertEquals(2, types.getFirst().getColumns().size());
		assertEquals("A B", types.getFirst().getColumns().getFirst().getName());
	}

	@Test
	void groupsTableParametersAcrossRoutinesInOneQuery() {
		var queries = new AtomicInteger();
		var reader = new SapHanaProcedureArgumentReader(DialectHolder.defaultDialect) {
			@Override protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context, ResultSetNextHandler handler) {
				queries.incrementAndGet();
				try {
					for (String routine : List.of("P1", "P2")) {
						handler.handleResultSetNext(row(routine, "SRC", "A B", "INTEGER"));
						handler.handleResultSetNext(row(routine, "SRC", "LABEL", "NVARCHAR"));
					}
				} catch (SQLException e) { throw new RuntimeException(e); }
				return null;
			}
		};
		var arguments = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, queries.get());
		assertEquals(2, arguments.size());
		for (var argument : arguments) {
			assertEquals(DataType.OTHER, argument.getDataType());
			assertTrue(argument.getDataTypeName().contains("\"A B\" INTEGER"), argument.getDataTypeName());
			assertTrue(argument.getDataTypeName().contains("LABEL NVARCHAR(20)"), argument.getDataTypeName());
			assertTrue(argument.getDataTypeName().indexOf("A B") < argument.getDataTypeName().indexOf("LABEL"));
		}
	}

	@Test
	void groupsFunctionReturnColumnsInOneQuery() {
		var queries = new AtomicInteger();
		var reader = new SapHanaFunctionReader(DialectHolder.defaultDialect) {
			@Override protected com.sqlapp.data.schemas.Function createFunction(ExResultSet rs) throws SQLException {
				return new com.sqlapp.data.schemas.Function(rs.getString("FUNCTION_NAME")).setSchemaName("APP");
			}
			@Override protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context, ResultSetNextHandler handler) {
				queries.incrementAndGet();
				try {
					for (String routine : List.of("F1", "F2")) {
						handler.handleResultSetNext(row(routine, "RETURN", "A B", "INTEGER"));
						handler.handleResultSetNext(row(routine, "RETURN", "LABEL", "NVARCHAR"));
					}
				} catch (SQLException e) { throw new RuntimeException(e); }
				return null;
			}
		};
		var functions = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, queries.get());
		assertEquals(2, functions.size());
		for (var function : functions) {
			assertTrue(function.getFunctionType().isTable());
			assertEquals(2, function.getReturning().getTable().getColumns().size());
			assertEquals("A B", function.getReturning().getTable().getColumns().get(0).getName());
		}
	}
}
