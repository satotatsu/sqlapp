package com.sqlapp.data.db.dialect.virtica.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.schemas.Schema;

class VirticaSchemaRestorerTest {
	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
	}

	private Schema schema() {
		Schema schema = new Schema("app");
		schema.setDefinition("CREATE SCHEMA app; CREATE PROCEDURE app.p() LANGUAGE PLvSQL AS $$ BEGIN NULL; END; $$;"
				+ "CREATE SCHEDULE app.s USING DATETIMES('2099-01-01'); CREATE TRIGGER app.t ON SCHEDULE s EXECUTE PROCEDURE app.p() AS DEFINER;");
		schema.getSpecifics().put("VERTICA_SCHEDULING_STATES",
				"TRIGGER \"app\".\"t\" ENABLED=false; SCHEDULE \"app\".\"s\" ENABLED=false;");
		return schema;
	}

	static class Jdbc extends VirticaSchedulingStateRestorerTest.Jdbc {
		boolean exists;
		int major = 25;
		int failDdl;
		List<String> attempted = new ArrayList<>();
		String generatedExport;
		int exports;
		List<Object[]> rawRows = List.of();
		int rawReads;

		@Override
		Connection connection() {
			var base = super.connection();
			return proxy(Connection.class, (p, m, a) -> {
				if (m.getName().equals("getMetaData"))
					return proxy(DatabaseMetaData.class, (mp, mm, ma) -> {
						if (mm.getName().equals("getDatabaseMajorVersion"))
							return major;
						throw new AssertionError(mm.getName());
					});
				if (m.getName().equals("prepareStatement") && ((String) a[0]).contains("v_catalog.schemata"))
					return proxy(PreparedStatement.class, (sp, sm, sa) -> switch (sm.getName()) {
					case "setString" -> {
						assertEquals("app", sa[1]);
						yield null;
					}
					case "executeQuery" -> rows(exists ? Collections.singletonList(new Object[] { "app" }) : List.of());
					case "close" -> null;
					default -> throw new AssertionError(sm.getName());
					});
				if (m.getName().equals("prepareStatement") && ((String) a[0]).contains("EXPORT_OBJECTS"))
					return proxy(PreparedStatement.class, (sp, sm, sa) -> switch (sm.getName()) {
					case "setString" -> {
						assertEquals("app", sa[1]);
						yield null;
					}
					case "executeQuery" -> {
						exports++;
						yield rows(Collections.singletonList(new Object[] { generatedExport }));
					}
					case "close" -> null;
					default -> throw new AssertionError(sm.getName());
					});
				if (m.getName().equals("prepareStatement") && ((String) a[0]).contains("c.character_maximum_length"))
					return proxy(PreparedStatement.class, (sp, sm, sa) -> switch (sm.getName()) {
					case "setString" -> {
						assertEquals("app", sa[1]);
						yield null;
					}
					case "executeQuery" -> {
						rawReads++;
						yield rows(rawRows);
					}
					case "close" -> null;
					default -> throw new AssertionError(sm.getName());
					});
				if (m.getName().equals("createStatement"))
					return proxy(Statement.class, (sp, sm, sa) -> switch (sm.getName()) {
					case "execute" -> {
						String sql = (String) sa[0];
						attempted.add(sql);
						if (attempted.size() == failDdl)
							throw new SQLException("test DDL failure");
						if (sql.startsWith("CREATE SCHEMA"))
							exists = true;
						yield true;
					}
					case "close" -> null;
					default -> throw new AssertionError(sm.getName());
					});
				try {
					return m.invoke(base, a);
				} catch (InvocationTargetException failure) {
					throw failure.getCause();
				}
			});
		}
	}

	@Test
	void flexBatchUsesOneExportForAnyObjectCountAndRetainsVerificationDetails() throws Exception {
		for (int count : new int[] { 1, 3 }) {
			String prefix = "CREATE SCHEMA app;";
			String generated = "";
			for (int i = 0; i < count; i++) {
				prefix += "CREATE FLEX TABLE app.f" + i + "(id INT);";
				generated += "CREATE PROJECTION app.p" + i + " AS SELECT id FROM app.f" + i + " UNSEGMENTED ALL NODES;";
			}
			Schema schema = new Schema("app");
			for (int i = 0; i < count; i++)
				VirticaFlexGeneratedObjectsTest.addRaw(schema, "f" + i);
			schema.setDefinition(prefix + generated
					+ "CREATE PROJECTION app.custom AS SELECT id FROM app.f0 UNSEGMENTED ALL NODES;");
			schema.getSpecifics().put("VERTICA_SCHEDULING_STATES", "");
			Jdbc jdbc = new Jdbc();
			jdbc.states.clear();
			jdbc.generatedExport = prefix + generated;
			jdbc.rawRows = new ArrayList<>();
			for (int i = 0; i < count; i++)
				jdbc.rawRows.add(new Object[] { "app", "f" + i, 130000L, false });
			var result = new VirticaSchemaRestorer().restoreWithDetails(jdbc.connection(), schema);
			assertEquals(count, result.verifiedGeneratedStatements().size());
			assertEquals(count + 2, result.result().completedStatements().size());
			assertEquals(1, jdbc.exports);
			assertEquals(2, jdbc.bulkReads); // state pre/post verification
			assertEquals(1, jdbc.rawReads);
			assertEquals(0, jdbc.mutations);
		}
	}

	@Test
	void changedGeneratedDesignFailsBeforeRemainingDdlAndReportsVerificationPhase() {
		Schema schema = new Schema("app");
		String prefix = "CREATE SCHEMA app; CREATE FLEX TABLE app.f(id INT);";
		VirticaFlexGeneratedObjectsTest.addRaw(schema, "f");
		schema.setDefinition(prefix + "CREATE PROJECTION app.p AS SELECT id FROM app.f UNSEGMENTED ALL NODES;");
		schema.getSpecifics().put("VERTICA_SCHEDULING_STATES", "");
		Jdbc jdbc = new Jdbc();
		jdbc.states.clear();
		jdbc.rawRows = Collections.singletonList(new Object[] { "app", "f", 130000L, false });
		jdbc.generatedExport = prefix
				+ "CREATE PROJECTION app.p AS SELECT id FROM app.f SEGMENTED BY hash(id) ALL NODES;";
		var failure = assertThrows(VirticaSchemaRestorer.RestorationException.class,
				() -> new VirticaSchemaRestorer().restoreWithDetails(jdbc.connection(), schema));
		assertEquals(VirticaSchemaRestorer.Phase.GENERATED_OBJECTS, failure.getPhase());
		assertEquals(2, failure.getCompletedStatements().size());
		assertEquals(2, jdbc.attempted.size());
		assertNull(failure.getFailedStatement());
	}

	@Test
	void executesFreshDefinitionThenComposesStateRestorationAndRejectsExistingTarget() throws Exception {
		Jdbc jdbc = new Jdbc();
		var result = new VirticaSchemaRestorer().restore(jdbc.connection(), schema());
		assertEquals(4, result.completedStatements().size());
		assertEquals(1, result.schedulingStates().applied().size());
		assertEquals(2, jdbc.configReads);
		assertThrows(SQLException.class, () -> new VirticaSchemaRestorer().restore(jdbc.connection(), schema()));
		assertEquals(4, jdbc.attempted.size());
	}

	@Test
	void runningSchedulerOlderServerAndExistingSchemaFailBeforeDdl() {
		for (int mode = 0; mode < 3; mode++) {
			Jdbc jdbc = new Jdbc();
			if (mode == 0)
				jdbc.scheduler = "1";
			if (mode == 1)
				jdbc.exists = true;
			if (mode == 2)
				jdbc.major = 24;
			assertThrows(SQLException.class, () -> new VirticaSchemaRestorer().restore(jdbc.connection(), schema()));
			assertTrue(jdbc.attempted.isEmpty());
		}
	}

	@Test
	void ddlAndStateFailuresExposePartialCompletionWithoutCleanup() {
		Jdbc ddl = new Jdbc();
		ddl.failDdl = 2;
		var failure = assertThrows(VirticaSchemaRestorer.RestorationException.class,
				() -> new VirticaSchemaRestorer().restore(ddl.connection(), schema()));
		assertEquals(1, failure.getCompletedStatements().size());
		assertTrue(failure.getFailedStatement().startsWith("CREATE PROCEDURE"));
		assertEquals(0, ddl.mutations);
		Jdbc states = new Jdbc();
		states.failMutation = 1;
		var stateFailure = assertThrows(VirticaSchemaRestorer.RestorationException.class,
				() -> new VirticaSchemaRestorer().restore(states.connection(), schema()));
		assertEquals(4, stateFailure.getCompletedStatements().size());
		assertNull(stateFailure.getFailedStatement());
		assertInstanceOf(VirticaSchedulingStateRestorer.RestorationException.class, stateFailure.getCause());
		assertTrue(states.exists);
	}
}
