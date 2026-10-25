/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2MetadataParityTest {
	static ExResultSet row(Map<String, String> values) {
		return new ExResultSet(null) {
			@Override
			public String getString(String name) {
				return values.get(name);
			}

			@Override
			public Object getObject(String name) {
				return values.get(name);
			}

			@Override
			public Timestamp getTimestamp(String name) {
				return null;
			}

			@Override
			public int getInt(String name) {
				return Integer.parseInt(values.getOrDefault(name, "0"));
			}

			@Override
			public long getLong(String name) {
				return Long.parseLong(values.getOrDefault(name, "0"));
			}

			@Override
			public boolean wasNull() {
				return false;
			}
		};
	}

	@Test
	void modernRoutineArgumentsRetainResolvedTypesAndDeclaredUnits() throws Exception {
		var values = new java.util.HashMap<String, String>(Map.of("PARMNAME", "P", "ROUTINE_NAME", "R", "SCHEMA_NAME",
				"APP", "SPECIFIC_NAME", "R_SPEC", "TYPENAME", "VARCHAR", "ROWTYPE", "P"));
		values.putAll(Map.of("TYPESCHEMA", "Type.Schema", "TYPEMODULENAME", "Module.Name", "LENGTH", "20", "SCALE", "0",
				"CODEPAGE", "1208", "TYPESTRINGUNITS", "CODEUNITS32", "STRINGUNITSLENGTH", "5", "LOCATOR", "N"));
		for (var argument : java.util.List.of(
				new Db2FunctionArgumentReader(DialectHolder.Db2_1110Dialect).createArgument(row(values)),
				new Db2ProcedureArgumentReader(DialectHolder.Db2_1110Dialect).createArgument(row(values)))) {
			for (String key : java.util.List.of("TYPESCHEMA", "TYPEMODULENAME", "TYPENAME", "LENGTH", "SCALE",
					"CODEPAGE", "TYPESTRINGUNITS", "STRINGUNITSLENGTH", "LOCATOR"))
				assertEquals(values.get(key), argument.getSpecifics().get(key), key);
			assertEquals(argument.getSpecifics(), argument.clone().getSpecifics());
		}
		assertFalse(new Db2FunctionArgumentReader(DialectHolder.defaultDialect).createArgument(row(values))
				.getSpecifics().containsKey("TYPEMODULENAME"));
	}

	@Test
	void routineArgumentsRetainLiteralSpecificIdentityAcrossVersions() throws Exception {
		for (var dialect : java.util.List.of(DialectHolder.defaultDialect, DialectHolder.Db2_1110Dialect)) {
			for (String specific : java.util.List.of("specific.with.dot", "specific\"with.dot", "ordinary")) {
				var values = Map.of("PARMNAME", "P", "ROUTINE_NAME", "R", "SCHEMA_NAME", "APP", "SPECIFIC_NAME",
						specific, "TYPENAME", "INTEGER", "ROWTYPE", "P");
				for (var argument : java.util.List.of(
						new Db2FunctionArgumentReader(dialect).createArgument(row(values)),
						new Db2ProcedureArgumentReader(dialect).createArgument(row(values)))) {
					assertEquals("APP", argument.getSchemaName());
					assertEquals("APP", argument.getRoutine().getSchemaName());
					assertEquals("R", argument.getRoutineName());
					assertEquals(specific, argument.getRoutine().getSpecificName());
				}
			}
		}
	}

	@Test
	void overloadedRoutineArgumentsAreReadInOneQueryPerKind() {
		AtomicInteger queries = new AtomicInteger();
		var functions = new Db2FunctionArgumentReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode sql, ParametersContext p,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				emitRoutineArguments(handler);
				return null;
			}
		}.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		var procedures = new Db2ProcedureArgumentReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode sql, ParametersContext p,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				emitRoutineArguments(handler);
				return null;
			}
		}.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(2, queries.get());
		for (var arguments : java.util.List.of(functions, procedures)) {
			assertEquals(40, arguments.size());
			for (int i = 0; i < arguments.size(); i++) {
				assertEquals("APP", arguments.get(i).getRoutine().getSchemaName());
				assertEquals("overload." + i / 2, arguments.get(i).getRoutine().getSpecificName());
				assertEquals(i % 2 == 0 ? com.sqlapp.jdbc.sql.ParameterDirection.Input
						: com.sqlapp.jdbc.sql.ParameterDirection.Inout, arguments.get(i).getDirection());
			}
		}
	}

	private static void emitRoutineArguments(ResultSetNextHandler handler) {
		for (int i = 0; i < 40; i++) {
			try {
				handler.handleResultSetNext(
						row(Map.of("PARMNAME", "P" + i % 2, "ROUTINE_NAME", "R", "SCHEMA_NAME", "APP", "SPECIFIC_NAME",
								"overload." + i / 2, "TYPENAME", "INTEGER", "ROWTYPE", i % 2 == 0 ? "P" : "B")));
			} catch (SQLException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	@Test
	void scalarReturnMetadataAndLiteralSpecificIdentityAreRetained() throws Exception {
		var reader = new Db2FunctionReader(DialectHolder.defaultDialect);
		reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
		var function = reader.createFunction(
				row(Map.of("routine_name", "F", "schema_name", "APP", "specific_name", "F.with.dot", "FUNCTIONTYPE",
						"S", "RETURN_TYPE_NAME", "DECIMAL", "RETURN_LENGTH", "12", "RETURN_SCALE", "4")));
		assertEquals("APP", function.getSchemaName());
		assertEquals("F.with.dot", function.getSpecificName());
		assertEquals(com.sqlapp.data.db.datatype.DataType.DECIMAL, function.getReturning().getDataType());
		assertEquals(Long.valueOf(12), function.getReturning().getLength());
		assertEquals(Integer.valueOf(4), function.getReturning().getScale());
	}

	@Test
	void indexUsesTableSchemaForAttachmentAndRetainsIndexSchema() {
		var reader = new Db2IndexReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				try {
					for (String order : java.util.List.of("D", "I")) {
						handler.handleResultSetNext(row(Map.of("schema_name", "INDEX_OWNER", "table_schema", "APP",
								"table_name", "T", "index_name", "IX", "UNIQUERULE", "U", "INDEXTYPE", "REG",
								"column_name", order.equals("D") ? "ID" : "PAYLOAD", "COLORDER", order)));
					}
				} catch (SQLException e) {
					throw new IllegalStateException(e);
				}
				return null;
			}
		};
		var indexes = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, indexes.size());
		Index index = indexes.get(0);
		assertEquals("APP", index.getSchemaName());
		assertEquals("INDEX_OWNER", index.getSpecifics().get("INDEX_SCHEMA"));
		assertEquals(Order.Desc, index.getColumns().get(0).getOrder());
		assertTrue(index.getColumns().get(1).isIncludedColumn());
	}

	@Test
	void tablePrivilegesAreExpandedWithOneSelectForMultipleObjects() {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Db2ObjectPrivilegeReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				assertNotNull(node);
				queries.incrementAndGet();
				for (int i = 0; i < 20; i++) {
					try {
						handler.handleResultSetNext(row(Map.of("schema_name", "APP", "object_name", "T" + i, "GRANTOR",
								"OWNER", "GRANTEE", "READER", "GRANTEETYPE", "R", "SELECTAUTH", "G", "UPDATEAUTH", "Y",
								"DELETEAUTH", "N")));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				}
				return null;
			}
		};
		var privileges = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, queries.get());
		assertEquals(40, privileges.size());
		assertEquals("SELECT", privileges.get(0).getPrivilege());
		assertTrue(privileges.get(0).isGrantable());
		assertFalse(privileges.get(1).isGrantable());
		assertEquals("R", privileges.get(0).getSpecifics().get("GRANTEE_TYPE"));
	}

	@Test
	void columnAndOverloadedRoutinePrivilegesRetainIdentity() throws Exception {
		var column = new Db2ColumnPrivilegeReader(DialectHolder.defaultDialect)
				.createPrivilege(row(Map.of("schema_name", "APP", "object_name", "T", "COLNAME", "C", "PRIVTYPE", "R",
						"GRANTABLE", "G", "GRANTEE", "TEAM", "GRANTEETYPE", "G")));
		assertEquals("REFERENCES", column.getPrivilege());
		assertTrue(column.isGrantable());
		assertEquals("C", column.getColumnName());
		var routine = new Db2RoutinePrivilegeReader(DialectHolder.defaultDialect)
				.createPrivilege(row(Map.of("schema_name", "APP", "object_name", "F", "SPECIFICNAME", "F_INT",
						"ROUTINETYPE", "F", "EXECUTEAUTH", "G", "GRANTEE", "READER", "GRANTEETYPE", "R")));
		assertEquals("F_INT", routine.getSpecificName());
		assertTrue(routine.isGrantable());
		assertEquals("F", routine.getSpecifics().get("ROUTINE_TYPE"));
	}

	@Test
	void aliasesUseVersionedCatalogsAndCorrectFilter() {
		var reader = new Db2SynonymReader(DialectHolder.defaultDialect);
		assertNotNull(reader.getSqlNode(new ProductVersionInfo().setMajorVersion(9).setMinorVersion(5)));
		assertNotNull(reader.getSqlNode(new ProductVersionInfo().setMajorVersion(9).setMinorVersion(7)));
		String oldSql = resource("synonyms.sql");
		String newSql = resource("synonyms970.sql");
		assertFalse(oldSql.contains("SYSCAT.MODULES"));
		assertFalse(oldSql.contains("BASE_SEQNAME"));
		assertTrue(newSql.contains("SYSCAT.MODULES"));
		assertFalse(newSql.contains("tableName"));
		assertFalse(newSql.contains("sequenceName"));
		assertEquals(3, newSql.split("isNotEmpty\\(synonymName\\)", -1).length - 1);
	}

	@Test
	void viewColumnsUseTheResolvedVersionReader() {
		for (int[] version : new int[][] { { 9, 5, 0 }, { 10, 5, 0 }, { 11, 5, 0 }, { 12, 1, 2 } }) {
			var dialect = DialectResolver.getInstance().getDialect("DB2", version[0], version[1], version[2]);
			var schema = dialect.getCatalogReader().getSchemaReader();
			assertEquals(schema.getTableReader().getColumnReader().getClass(),
					schema.getViewReader().getColumnReader().getClass());
			assertNotNull(dialect.getCatalogReader().getObjectPrivilegeReader());
			assertNotNull(dialect.getCatalogReader().getColumnPrivilegeReader());
			assertNotNull(dialect.getCatalogReader().getRoutinePrivilegeReader());
		}
	}

	@Test
	void roleMemberSqlIsResolvable() {
		assertNotNull(new Db2RoleMemberReader(DialectHolder.defaultDialect).getSqlNode(new ProductVersionInfo()));
	}

	@Test
	void routineArgumentsExcludeReturnColumnsAndFilterByRoutineName() {
		for (String name : java.util.List.of("functionArguments.sql", "procedureArguments.sql")) {
			String sql = resource(name);
			assertTrue(sql.contains("r.ROWTYPE IN ('B', 'O', 'P')"));
			assertTrue(sql.contains("isNotEmpty(routineName)"));
		}
	}

	private static String resource(String name) {
		try (var stream = Db2MetadataParityTest.class.getResourceAsStream(name)) {
			assertNotNull(stream, name);
			return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
