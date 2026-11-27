package com.sqlapp.data.db.dialect.vertica.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.List;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.vertica.metadata.Vertica11_1_1ProcedureReader;
import com.sqlapp.data.db.dialect.vertica.metadata.VerticaDdlExporter;
import com.sqlapp.data.db.dialect.vertica.metadata.VerticaIndexReader;
import com.sqlapp.data.schemas.Projection;

class VerticaDdlExporterTest {
	@Test
	void schemaSchedulingStatesAreBulkCapturedAndInvalidRowsKeepOldDefinition() throws Exception {
		var schema = new com.sqlapp.data.schemas.Schema("app");
		Jdbc jdbc = new Jdbc();
		jdbc.states = List.<Object[]>of(new Object[] { "TRIGGER", "app", "t'quote", false },
				new Object[] { "SCHEDULE", "app", "orphan", true });
		String script = new VerticaDdlExporter().exportSchemaDefinition(jdbc.connection(), schema);
		assertEquals(2, jdbc.queries);
		String states = schema.getSpecifics().get("VERTICA_SCHEDULING_STATES");
		assertTrue(states.contains("TRIGGER \"app\".\"t'quote\" ENABLED=false"), states);
		assertTrue(states.contains("SCHEDULE \"app\".\"orphan\" ENABLED=true"), states);
		com.sqlapp.data.schemas.Schema xml = com.sqlapp.data.schemas.SchemaUtils
				.readXml(new java.io.StringReader(schema.asXml()));
		assertEquals(states, xml.getSpecifics().get("VERTICA_SCHEDULING_STATES"));
		for (Object[] invalid : List.<Object[]>of(new Object[] { "TRIGGER", "other", "t", true },
				new Object[] { "UNKNOWN", "app", "t", true }, new Object[] { "TRIGGER", "app", "t", null })) {
			Jdbc failed = new Jdbc();
			failed.states = List.<Object[]>of(invalid);
			assertThrows(SQLException.class,
					() -> new VerticaDdlExporter().exportSchemaDefinition(failed.connection(), schema));
			assertEquals(script, String.join("\n", schema.getDefinition()));
			assertEquals(states, schema.getSpecifics().get("VERTICA_SCHEDULING_STATES"));
		}
		Jdbc older = new Jdbc();
		older.major = 24;
		new VerticaDdlExporter().exportSchemaDefinition(older.connection(), schema);
		assertEquals(1, older.queries);
		assertFalse(schema.getSpecifics().containsKey("VERTICA_SCHEDULING_STATES"));
	}

	@Test
	void schemaExportCapturesOneScopedScriptAndFailurePreservesDefinition() throws Exception {
		var schema = new com.sqlapp.data.schemas.Schema("app_schema");
		Jdbc jdbc = new Jdbc();
		String script = new VerticaDdlExporter().exportSchemaDefinition(jdbc.connection(), schema);
		assertEquals(2, jdbc.queries);
		assertEquals("app_schema", jdbc.scope);
		assertEquals(script, String.join("\n", schema.getDefinition()));
		Jdbc failed = new Jdbc();
		failed.script = " ";
		assertThrows(SQLException.class,
				() -> new VerticaDdlExporter().exportSchemaDefinition(failed.connection(), schema));
		assertEquals(script, String.join("\n", schema.getDefinition()));
		schema.setName(" ");
		assertThrows(IllegalArgumentException.class,
				() -> new VerticaDdlExporter().exportSchemaDefinition(null, schema));
	}

	@Test
	void textIndexesExportOnceUsingOwnSchemasAndAssignOnlyTheirCreates() throws Exception {
		Jdbc jdbc = new Jdbc();
		var first = new com.sqlapp.data.schemas.Index("one");
		first.setIndexType(com.sqlapp.data.schemas.IndexType.FullText);
		first.setSchemaName("source");
		first.setTableName("data");
		first.getSpecifics().put("INDEX_SCHEMA_NAME", "indexes");
		var second = first.clone();
		second.setName("two");
		jdbc.script = "CREATE TEXT INDEX indexes.one ON source.data(id,body) TOKENIZER v_txtindex.StringTokenizer(LONG VARCHAR);"
				+ "CREATE TEXT INDEX indexes.two ON source.data(id,body) TOKENIZER v_txtindex.StringTokenizer(LONG VARCHAR);";
		new VerticaIndexReader(com.sqlapp.data.db.dialect.vertica.DialectHolder.defaultDialect11_1_1).setMetadataDetail(
				jdbc.connection(), new com.sqlapp.data.parameter.ParametersContext(), List.of(first, second));
		assertEquals(1, jdbc.queries);
		assertEquals("\"indexes\".\"one\",\"indexes\".\"two\"", jdbc.scope);
		assertFalse(first.getDefinition().isEmpty());
		assertEquals("", new VerticaDdlExporter().exportTextIndexDefinitions(null, List.of()));
		Jdbc older = new Jdbc();
		older.major = 24;
		var reader = new VerticaIndexReader(com.sqlapp.data.db.dialect.vertica.DialectHolder.defaultDialect11_1_1);
		reader.setMetadataDetail(older.connection(), new com.sqlapp.data.parameter.ParametersContext(), List.of(first));
		assertEquals(0, older.queries);
		reader.setMetadataDetail(null, new com.sqlapp.data.parameter.ParametersContext(), List.of());
	}

	@Test
	void procedureOverloadsUseOneBoundExportAndEmptySetsUseNone() throws Exception {
		Jdbc jdbc = new Jdbc();
		jdbc.script = "CREATE PROCEDURE app.p(x int) LANGUAGE 'PL/vSQL' AS $$ BEGIN NULL; END; $$;"
				+ "CREATE PROCEDURE app.p(x varchar) LANGUAGE 'PL/vSQL' AS $$ BEGIN NULL; END; $$;";
		var integer = new com.sqlapp.data.schemas.Procedure("p");
		integer.setSchemaName("app");
		integer.getArguments().add(new com.sqlapp.data.schemas.NamedArgument("x")
				.setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT));
		var string = integer.clone();
		string.getArguments().get(0).setDataType(com.sqlapp.data.db.datatype.DataType.VARCHAR);
		var reader = new Vertica11_1_1ProcedureReader(
				com.sqlapp.data.db.dialect.vertica.DialectHolder.defaultDialect11_1_1);
		reader.setMetadataDetail(jdbc.connection(), new com.sqlapp.data.parameter.ParametersContext(),
				List.of(integer, string));
		assertEquals(1, jdbc.queries);
		assertTrue(jdbc.scope.contains("\"app\".\"p\"(BIGINT)"), jdbc.scope);
		assertTrue(jdbc.scope.contains("\"app\".\"p\"(VARCHAR)"), jdbc.scope);
		assertFalse(jdbc.scope.contains("VARCHAR(1)"), jdbc.scope);
		assertFalse(integer.getDefinition().isEmpty());
		assertFalse(string.getDefinition().isEmpty());
		assertEquals("", new VerticaDdlExporter().exportProcedureDefinitions(null, List.of()));
		reader.setMetadataDetail(null, new com.sqlapp.data.parameter.ParametersContext(), List.of());
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
	}

	private static class Jdbc {
		List<Object[]> states = List.of();
		int major = 25;
		int prepares;
		int queries;
		int rows = 1;
		int position;
		String sql;
		String scope;
		String script = "CREATE TABLE app.t(id INT);\nCREATE PROJECTION app.p AS SELECT id FROM app.t UNSEGMENTED ALL NODES;";
		boolean statementClosed;
		boolean resultClosed;
		boolean connectionClosed;

		Connection connection() {
			return proxy(Connection.class, (p, m, args) -> {
				if (m.getName().equals("getMetaData"))
					return proxy(DatabaseMetaData.class, (mp, mm, ma) -> {
						if (mm.getName().equals("getDatabaseMajorVersion"))
							return major;
						throw new AssertionError(mm.getName());
					});
				if (m.getName().equals("close")) {
					connectionClosed = true;
					return null;
				}
				if (!m.getName().equals("prepareStatement"))
					throw new AssertionError(m.getName());
				prepares++;
				sql = (String) args[0];
				if (sql.contains("v_catalog.user_schedules")) {
					int[] statePosition = { -1 };
					return proxy(PreparedStatement.class, (sp, sm, sa) -> switch (sm.getName()) {
					case "setString" -> {
						assertTrue(sa[0].equals(1) || sa[0].equals(2));
						assertEquals(scope, sa[1]);
						yield null;
					}
					case "close" -> null;
					case "executeQuery" -> {
						queries++;
						yield proxy(ResultSet.class, (rp, rm, ra) -> switch (rm.getName()) {
						case "next" -> ++statePosition[0] < states.size();
						case "getString" -> (String) states.get(statePosition[0])[(Integer) ra[0] - 1];
						case "getBoolean" -> Boolean.TRUE.equals(states.get(statePosition[0])[3]);
						case "wasNull" -> states.get(statePosition[0])[3] == null;
						case "close" -> null;
						default -> throw new AssertionError(rm.getName());
						});
					}
					default -> throw new AssertionError(sm.getName());
					});
				}
				return proxy(PreparedStatement.class, (sp, sm, sa) -> switch (sm.getName()) {
				case "setString" -> {
					assertEquals(1, sa[0]);
					scope = (String) sa[1];
					yield null;
				}
				case "close" -> {
					statementClosed = true;
					yield null;
				}
				case "executeQuery" -> {
					queries++;
					yield proxy(ResultSet.class, (rp, rm, ra) -> switch (rm.getName()) {
					case "next" -> ++position <= rows;
					case "getString" -> {
						assertEquals(1, ra[0]);
						yield script;
					}
					case "close" -> {
						resultClosed = true;
						yield null;
					}
					default -> throw new AssertionError(rm.getName());
					});
				}
				default -> throw new AssertionError(sm.getName());
				});
			});
		}
	}

	private Projection projection(String name) {
		Projection p = new Projection(name);
		p.setSchemaName("app");
		return p;
	}

	@Test
	void exportsRequestedSetOnceWithBoundScopeAndRetainsDependencyScript() throws Exception {
		Jdbc jdbc = new Jdbc();
		Projection first = projection("p\"name");
		Projection second = projection("other");
		String result = new VerticaDdlExporter().exportObjects(jdbc.connection(), List.of(first, second, first));
		assertEquals(1, jdbc.prepares);
		assertEquals(1, jdbc.queries);
		assertEquals("SELECT EXPORT_OBJECTS('', ?, false)", jdbc.sql);
		assertEquals("\"app\".\"p\"\"name\",\"app\".\"other\"", jdbc.scope);
		assertEquals(jdbc.script, result);
		assertTrue(jdbc.statementClosed);
		assertTrue(jdbc.resultClosed);
		assertFalse(jdbc.connectionClosed);
		assertTrue(first.getDefinition() == null || first.getDefinition().isEmpty());
	}

	@Test
	void emptyAndUnrepresentableScopesNeverBecomeExportAll() throws Exception {
		var exporter = new VerticaDdlExporter();
		assertEquals("", exporter.exportObjects(null, List.of()));
		Jdbc jdbc = new Jdbc();
		Projection missingSchema = new Projection("p");
		assertThrows(IllegalArgumentException.class,
				() -> exporter.exportObjects(jdbc.connection(), List.of(missingSchema)));
		assertThrows(IllegalArgumentException.class,
				() -> exporter.exportObjects(jdbc.connection(), List.of(projection("p,other"))));
		var function = new com.sqlapp.data.schemas.Function("overloaded");
		function.setSchemaName("app");
		assertThrows(IllegalArgumentException.class,
				() -> exporter.exportObjects(jdbc.connection(), List.of(function)));
		assertEquals(0, jdbc.prepares);
	}

	@Test
	void invalidServerResultsFailAndCloseResources() {
		for (int rows : new int[] { 0, 1, 2 }) {
			Jdbc jdbc = new Jdbc();
			jdbc.rows = rows;
			if (rows == 1)
				jdbc.script = " ";
			assertThrows(SQLException.class,
					() -> new VerticaDdlExporter().exportObjects(jdbc.connection(), List.of(projection("p"))));
			assertTrue(jdbc.resultClosed);
			assertTrue(jdbc.statementClosed);
			assertFalse(jdbc.connectionClosed);
		}
	}

	@Test
	void highLevelProjectionExportUsesOneQueryThenAssignsValidatedDefinitions() throws Exception {
		Jdbc jdbc = new Jdbc();
		Projection first = projection("p1");
		Projection second = projection("p2");
		jdbc.script = "CREATE TABLE app.t(id INT); CREATE PROJECTION app.p1 AS SELECT id FROM app.t; CREATE PROJECTION app.p2 AS SELECT id FROM app.t;";
		String script = new VerticaDdlExporter().exportProjectionDefinitions(jdbc.connection(), List.of(first, second));
		assertEquals(jdbc.script, script);
		assertEquals(1, jdbc.queries);
		assertTrue(String.join("\n", first.getDefinition()).contains("app.p1"));
		assertFalse(String.join("\n", first.getDefinition()).contains("app.p2"));
		assertFalse(String.join("\n", second.getDefinition()).contains("CREATE TABLE"));
		assertEquals("", new VerticaDdlExporter().exportProjectionDefinitions(null, List.of()));
	}

	@Test
	void highLevelMappingFailurePreservesOldDefinitionsAndRejectsDuplicateTargetsBeforeQuery() throws Exception {
		Jdbc jdbc = new Jdbc();
		Projection first = projection("p1");
		Projection second = projection("p2");
		first.setDefinition("old first");
		second.setDefinition("old second");
		jdbc.script = "CREATE PROJECTION app.p1 AS SELECT id FROM app.t;";
		var exporter = new VerticaDdlExporter();
		assertThrows(IllegalArgumentException.class,
				() -> exporter.exportProjectionDefinitions(jdbc.connection(), List.of(first, second)));
		assertEquals(List.of("old first"), first.getDefinition());
		assertEquals(List.of("old second"), second.getDefinition());
		assertEquals(1, jdbc.queries);
		assertTrue(jdbc.resultClosed);
		assertTrue(jdbc.statementClosed);
		Jdbc untouched = new Jdbc();
		assertThrows(IllegalArgumentException.class,
				() -> exporter.exportProjectionDefinitions(untouched.connection(), List.of(first, projection("P1"))));
		assertEquals(0, untouched.prepares);
	}
}
