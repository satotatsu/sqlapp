package com.sqlapp.data.db.dialect.virtica.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.schemas.Projection;

class VirticaDdlExporterTest {
	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
	}

	private static class Jdbc {
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
				if (m.getName().equals("close")) {
					connectionClosed = true;
					return null;
				}
				if (!m.getName().equals("prepareStatement"))
					throw new AssertionError(m.getName());
				prepares++;
				sql = (String) args[0];
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
		String result = new VirticaDdlExporter().exportObjects(jdbc.connection(), List.of(first, second, first));
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
		var exporter = new VirticaDdlExporter();
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
					() -> new VirticaDdlExporter().exportObjects(jdbc.connection(), List.of(projection("p"))));
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
		String script = new VirticaDdlExporter().exportProjectionDefinitions(jdbc.connection(), List.of(first, second));
		assertEquals(jdbc.script, script);
		assertEquals(1, jdbc.queries);
		assertTrue(String.join("\n", first.getDefinition()).contains("app.p1"));
		assertFalse(String.join("\n", first.getDefinition()).contains("app.p2"));
		assertFalse(String.join("\n", second.getDefinition()).contains("CREATE TABLE"));
		assertEquals("", new VirticaDdlExporter().exportProjectionDefinitions(null, List.of()));
	}

	@Test
	void highLevelMappingFailurePreservesOldDefinitionsAndRejectsDuplicateTargetsBeforeQuery() throws Exception {
		Jdbc jdbc = new Jdbc();
		Projection first = projection("p1");
		Projection second = projection("p2");
		first.setDefinition("old first");
		second.setDefinition("old second");
		jdbc.script = "CREATE PROJECTION app.p1 AS SELECT id FROM app.t;";
		var exporter = new VirticaDdlExporter();
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
