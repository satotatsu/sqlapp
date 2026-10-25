/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.metadata.ReaderOptions;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2RoutineReplayTest {
	private Map<String, String> fields(boolean function) {
		String kind = function ? "FUNCTION" : "PROCEDURE", name = function ? "F" : "P";
		var fields = new HashMap<String, String>(Map.of("routine_name", name, "schema_name", "APP", "specific_name",
				name + "_SPEC", "LANGUAGE", "SQL", "TEXT_BODY_OFFSET", "-1", "QUALIFIER", "BASE.SCHEMA", "FUNC_PATH",
				"\"SYSIBM\",\"Fns.Q\"", "REMARKS", "routine's comment", "DIALECT", "SQL PL", "VALID", "Y"));
		fields.put("ROUTINE_DEFINITION",
				"CREATE " + kind + " APP." + name + "() " + (function ? "RETURNS INTEGER " : "") + "SPECIFIC " + name
						+ "_SPEC LANGUAGE SQL " + (function ? "RETURN 1" : "BEGIN END"));
		fields.put("FUNCTIONTYPE", "S");
		fields.put("RETURN_TYPE_NAME", "INTEGER");
		return fields;
	}

	private Routine<?> read(boolean function, Map<String, String> values, ReaderOptions options) throws SQLException {
		if (function) {
			var reader = new Db2FunctionReader(DialectHolder.Db2_1110Dialect);
			reader.setReaderOptions(options);
			return reader.createFunction(Db2MetadataParityTest.row(values));
		}
		var reader = new Db2ProcedureReader(DialectHolder.Db2_1110Dialect);
		reader.setReaderOptions(options);
		return reader.createProcedure(Db2MetadataParityTest.row(values));
	}

	private List<String> sql(Routine<?> routine, SqlType type) throws Exception {
		Routine<?> restored = SchemaUtils.readXml(new java.io.StringReader(routine.asXml()));
		SqlFactory<Routine<?>> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry()
				.getSqlFactory(restored, type);
		return factory.createSql(restored).stream().map(o -> o.getSqlText().replaceAll("\\s+", " ")).toList();
	}

	private Function function() {
		Function function = new Function("F").setSchemaName("APP").setStatement("RETURN 1");
		function.getReturning().setDataType(DataType.INT);
		return function;
	}

	@Test
	void completeDefinitionsRestoreContextAndSpecificCommentsForBothRoutineKinds() throws Exception {
		ReaderOptions options = new ReaderOptions();
		options.setReadDefinition(true);
		for (boolean function : List.of(true, false)) {
			var fields = fields(function);
			Routine<?> routine = read(function, fields, options);
			var sql = sql(routine, SqlType.CREATE);
			assertEquals("SET CURRENT SCHEMA = 'BASE.SCHEMA'", sql.get(0));
			assertEquals("SET CURRENT PATH = \"SYSIBM\", \"Fns.Q\"", sql.get(1));
			assertEquals(fields.get("ROUTINE_DEFINITION"), sql.get(2));
			assertEquals("COMMENT ON SPECIFIC " + (function ? "FUNCTION APP.F_SPEC" : "PROCEDURE APP.P_SPEC")
					+ " IS 'routine''s comment'", sql.get(3));
		}
	}

	@Test
	void definitionsAreOrderedSeparateOperationsAndOwnEmbeddedOptions() throws Exception {
		for (Routine<?> routine : List.of(function(), new Procedure("P").setSchemaName("APP"))) {
			routine.setDefinition(
					"--#SET TERMINATOR @\nCREATE " + (routine instanceof Function ? "FUNCTION" : "PROCEDURE") + " APP."
							+ routine.getName() + "() LANGUAGE SQL BEGIN END@");
			routine.getDefinition().add("COMMENT ON SPECIFIC "
					+ (routine instanceof Function ? "FUNCTION" : "PROCEDURE") + " APP.X IS 'native'@");
			routine.getSpecifics().put("COMMIT_ON_RETURN", "invalid but embedded in full DDL");
			var sql = sql(routine, SqlType.CREATE);
			assertEquals(2, sql.size());
			assertTrue(sql.get(0).startsWith("CREATE"));
			assertTrue(sql.get(1).startsWith("COMMENT"));
		}
	}

	@Test
	void defaultReaderPreservesNativeDdlWhenBodyIsUnavailableOrCannotBeRebuilt() throws Exception {
		for (boolean function : List.of(true, false)) {
			var values = fields(function);
			assertFalse(read(function, values, new ReaderOptions()).getDefinition().isEmpty());
			values.put("TEXT_BODY_OFFSET", "99999");
			assertFalse(read(function, values, new ReaderOptions()).getDefinition().isEmpty());
			values.put("TEXT_BODY_OFFSET", "10");
			values.put("DIALECT", "PL/SQL");
			assertFalse(read(function, values, new ReaderOptions()).getDefinition().isEmpty());
			values.put("DIALECT", "SQL PL");
			values.put("AUTONOMOUS", "Y");
			assertFalse(read(function, values, new ReaderOptions()).getDefinition().isEmpty());
			ReaderOptions none = new ReaderOptions();
			none.setReadStatement(false);
			assertTrue(read(function, values, none).getDefinition().isEmpty());
		}
	}

	@Test
	void procedureBodyReplayIncludesResultsAccessCommitAndDeterminism() throws Exception {
		var values = fields(false);
		String body = "BEGIN DECLARE C CURSOR WITH RETURN FOR SELECT 1 FROM SYSIBM.SYSDUMMY1; OPEN C; END";
		String ddl = "CREATE PROCEDURE APP.P() LANGUAGE SQL " + body;
		values.put("ROUTINE_DEFINITION", ddl);
		values.put("TEXT_BODY_OFFSET", Integer.toString(ddl.indexOf("BEGIN")));
		values.putAll(Map.of("RESULT_SETS", "2", "SQL_DATA_ACCESS", "R", "COMMIT_ON_RETURN", "N", "DETERMINISTIC", "N",
				"AUTONOMOUS", "N"));
		Routine<?> routine = read(false, values, new ReaderOptions());
		assertTrue(routine.getDefinition().isEmpty());
		String generated = sql(routine, SqlType.CREATE).get(2);
		assertTrue(generated.contains("DYNAMIC RESULT SETS 2"), generated);
		assertTrue(generated.contains("READS SQL DATA NOT DETERMINISTIC COMMIT ON RETURN NO"), generated);
		assertTrue(generated.endsWith(body), generated);
	}

	@Test
	void procedureOptionsValidateAndSupportBothCommitModes() throws Exception {
		Procedure procedure = new Procedure("P").setSchemaName("APP").setStatement("BEGIN END");
		procedure.getSpecifics().putAll(
				Map.of("RESULT_SETS", "0", "SQL_DATA_ACCESS", "M", "COMMIT_ON_RETURN", "Y", "DETERMINISTIC", "Y"));
		assertTrue(sql(procedure, SqlType.CREATE).getFirst()
				.contains("MODIFIES SQL DATA DETERMINISTIC COMMIT ON RETURN YES"));
		for (var entry : Map.of("RESULT_SETS", "-1", "SQL_DATA_ACCESS", "?", "COMMIT_ON_RETURN", "?", "DETERMINISTIC",
				"?", "AUTONOMOUS", "Y").entrySet()) {
			Procedure invalid = procedure.clone();
			invalid.getSpecifics().put(entry.getKey(), entry.getValue());
			assertThrows(IllegalArgumentException.class, () -> sql(invalid, SqlType.CREATE));
		}
		procedure.getSpecifics().put("RESULT_SETS", "32768");
		assertThrows(IllegalArgumentException.class, () -> sql(procedure, SqlType.CREATE));
	}

	@Test
	void quotedRoutineSpecificArgumentAndReturnNamesAreEscapedForCreateAndDrop() throws Exception {
		Function function = function();
		function.setName("F\"Q");
		function.setSchemaName("S\"Q");
		function.setSpecificNameLiteral("Specific\".Q");
		function.getArguments().add(new NamedArgument("A\"Q").setDataType(DataType.INT));
		function.setFunctionType(FunctionType.Table);
		function.getReturning().toTable();
		function.getReturning().getTable().getColumns().add("C\"Q", c -> c.setDataType(DataType.INT));
		String ddl = sql(function, SqlType.CREATE).getFirst();
		for (String expected : List.of("\"S\"\"Q\".\"F\"\"Q\"", "\"A\"\"Q\" INTEGER", "\"C\"\"Q\" INTEGER",
				"SPECIFIC \"Specific\"\".Q\""))
			assertTrue(ddl.contains(expected), ddl);
		assertEquals("DROP SPECIFIC FUNCTION \"S\"\"Q\".\"Specific\"\".Q\"", sql(function, SqlType.DROP).getFirst());
		Procedure procedure = new Procedure("P\"Q").setSchemaName("S\"Q").setSpecificNameLiteral("P\".Q")
				.setStatement("BEGIN END");
		procedure.getArguments().add(new NamedArgument("A\"Q").setDataType(DataType.INT));
		assertTrue(sql(procedure, SqlType.CREATE).getFirst().contains("\"P\"\"Q\""));
		assertEquals("DROP SPECIFIC PROCEDURE \"S\"\"Q\".\"P\"\".Q\"", sql(procedure, SqlType.DROP).getFirst());
	}

	@Test
	void invalidTextPathsAndUnsupportedBodyDialectsRequireCompleteDefinitions() throws Exception {
		Function function = function();
		function.getSpecifics().put("FUNC_PATH", "\"unterminated");
		assertThrows(IllegalArgumentException.class, () -> sql(function, SqlType.CREATE));
		function.getSpecifics().clear();
		function.setDefinition(" \0");
		SqlFactory<Function> direct = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(function,
				SqlType.CREATE);
		assertThrows(IllegalArgumentException.class, () -> direct.createSql(function));
		function.setDefinition((String) null);
		function.getSpecifics().put("DIALECT", "PL/SQL");
		assertThrows(IllegalArgumentException.class, () -> sql(function, SqlType.CREATE));
	}

	@Test
	void twentyRoutineHeadersOfEachKindUseOneQueryEachAndLegacyContextIsUntouched() throws Exception {
		AtomicInteger queries = new AtomicInteger();
		var functions = new Db2FunctionReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode sql, ParametersContext p,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				emit(handler, true);
				return null;
			}
		};
		var procedures = new Db2ProcedureReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode sql, ParametersContext p,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				emit(handler, false);
				return null;
			}
		};
		functions.setReaderOptions(new ReaderOptions());
		procedures.setReaderOptions(new ReaderOptions());
		assertEquals(20, functions.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).size());
		assertEquals(20, procedures.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).size());
		assertEquals(2, queries.get());
		var legacy = new Db2ProcedureReader(DialectHolder.Db2_1050Dialect);
		legacy.setReaderOptions(new ReaderOptions());
		assertFalse(legacy.createProcedure(Db2MetadataParityTest.row(fields(false))).getSpecifics()
				.containsKey("QUALIFIER"));
	}

	private void emit(ResultSetNextHandler handler, boolean function) {
		for (int i = 0; i < 20; i++)
			try {
				var values = fields(function);
				values.put("routine_name", (function ? "F" : "P") + i);
				handler.handleResultSetNext(Db2MetadataParityTest.row(values));
			} catch (SQLException e) {
				throw new IllegalStateException(e);
			}
	}
}
