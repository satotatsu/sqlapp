package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Event;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Oracle23aiEventReaderTest {
	@Test
	void textRepresentationIsModernOnlyAndInvalidFlagsFail() throws Exception {
		var modern = new Oracle23aiEventReader(DialectHolder.oracle26aiDialect);
		assertEquals("schedulerJobs23ai.sql", modern.jobSql());
		assertEquals("schedulerJobs10g.sql", new Oracle10gEventReader(DialectHolder.oracle19cDialect).jobSql());
		for (String flag : List.of("TEXT", "UNSUPPORTED")) {
			Event event = event("J1");
			modern.readArgumentRepresentation(new ExResultSet(null) {
				@Override
				public String getString(String name) {
					return flag;
				}
			}, event, "oracle.scheduler.argument.1.");
			Event restored = SchemaUtils.readXml(new java.io.StringReader(event.asXml()));
			assertEquals(flag, restored.getSpecifics().get("oracle.scheduler.argument.1.representation"));
		}
		assertThrows(SQLException.class, () -> modern.readArgumentRepresentation(new ExResultSet(null) {
			@Override
			public String getString(String name) {
				return "unknown";
			}
		}, event("J1"), "oracle.scheduler.argument.1."));
		try (var oldSql = Oracle10gEventReader.class.getResourceAsStream("schedulerJobs10g.sql")) {
			assertFalse(new String(oldSql.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
					.contains("STANDARD_HASH"));
		}
	}

	private Event event(String name) {
		Event event = new Event(name).setSchemaName("APP").setEnable(false);
		event.setStatement("BEGIN NULL; END;");
		for (String kind : List.of("PROGRAM", "SCHEDULE")) {
			event.getSpecifics().put("oracle.scheduler." + kind + "_OWNER", "SHARED");
			event.getSpecifics().put("oracle.scheduler." + kind + "_NAME", kind);
			event.getSpecifics().put("oracle.scheduler." + kind.toLowerCase(java.util.Locale.ROOT) + ".status",
					"AVAILABLE");
		}
		return event;
	}

	private String script(String kind, String name) {
		return "BEGIN\n dbms_scheduler.create_" + kind.toLowerCase(java.util.Locale.ROOT) + "('" + name + "');\n"
				+ " -- 日本語 'quoted'\n dbms_scheduler.enable('" + name + "');\nEND;\n/\n";
	}

	private Oracle23aiEventReader reader(int[] queries, int deniedCode, boolean missing, boolean duplicate) {
		return new Oracle23aiEventReader(DialectHolder.oracle26aiDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				queries[0]++;
				try {
					for (String[] object : new String[][] { { "JOB", "APP", "J1" }, { "JOB", "APP", "J2" },
							{ "PROGRAM", "SHARED", "PROGRAM" }, { "SCHEDULE", "SHARED", "SCHEDULE" } }) {
						if (object[2].equals("J2")) {
							if (deniedCode != 0)
								throw new SQLException("metadata error", "", deniedCode);
							if (missing)
								continue;
						}
						ExResultSet row = new ExResultSet(null) {
							@Override
							public String getString(String key) {
								return switch (key.toUpperCase(java.util.Locale.ROOT)) {
								case "OBJECT_KIND" -> object[0];
								case "OWNER" -> object[1];
								case "OBJECT_NAME" -> object[2];
								case "OBJECT_DEFINITION" -> script(object[0], object[2]);
								default -> null;
								};
							}
						};
						h.handleResultSetNext(row);
						if (duplicate)
							h.handleResultSetNext(row);
					}
				} catch (SQLException error) {
					throw new RuntimeException(error);
				}
				return null;
			}
		};
	}

	@Test
	void capturesTheCompleteSharedBatchAndPreservesScriptsWithoutRewritingOrExecuting() throws Exception {
		Event first = event("J1"), second = event("J2");
		int[] queries = { 0 };
		reader(queries, 0, false, false).setMetadataDetail(null, new ParametersContext(), List.of(first, second));
		assertEquals(1, queries[0]);
		Event restored = SchemaUtils.readXml(new java.io.StringReader(first.asXml()));
		assertTrue(restored.getDefinition().isEmpty());
		assertEquals(script("JOB", "J1"), restored.getSpecifics().get("oracle.scheduler.definition"));
		assertEquals("AVAILABLE", restored.getSpecifics().get("oracle.scheduler.definitionStatus"));
		assertEquals("SOURCE_OWNER", restored.getSpecifics().get("oracle.scheduler.definitionContext"));
		assertEquals("APP", restored.getSpecifics().get("oracle.scheduler.definitionOwner"));
		assertEquals("J1", restored.getSpecifics().get("oracle.scheduler.definitionName"));
		assertEquals("SHARED", restored.getSpecifics().get("oracle.scheduler.program.definitionOwner"));
		assertEquals("PROGRAM", restored.getSpecifics().get("oracle.scheduler.program.definitionName"));
		assertEquals(script("PROGRAM", "PROGRAM"), restored.getSpecifics().get("oracle.scheduler.program.definition"));
		assertEquals(script("SCHEDULE", "SCHEDULE"),
				restored.getSpecifics().get("oracle.scheduler.schedule.definition"));
		assertEquals(first.getSpecifics().get("oracle.scheduler.program.definition"),
				second.getSpecifics().get("oracle.scheduler.program.definition"));
		assertFalse(restored.isEnable());
		assertEquals(com.sqlapp.data.db.dialect.oracle.util.OracleSchedulerDefinition.fingerprint(restored), restored
				.getSpecifics().get(com.sqlapp.data.db.dialect.oracle.util.OracleSchedulerDefinition.FINGERPRINT));
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE));
	}

	@Test
	void deniedMetadataDiscardsTheWholePartialBatchButRetainsInventory() throws Exception {
		for (int code : new int[] { 1031, 31603 }) {
			Event first = event("J1"), second = event("J2");
			reader(new int[1], code, false, false).setMetadataDetail(null, new ParametersContext(),
					List.of(first, second));
			assertTrue(first.getDefinition().isEmpty());
			assertTrue(second.getDefinition().isEmpty());
			assertFalse(first.getSpecifics().containsKey("oracle.scheduler.definition"));
			assertFalse(second.getSpecifics().containsKey("oracle.scheduler.definition"));
			assertEquals("INACCESSIBLE", first.getSpecifics().get("oracle.scheduler.definitionStatus"));
			assertEquals("INACCESSIBLE", first.getSpecifics().get("oracle.scheduler.program.definitionStatus"));
			assertEquals("AVAILABLE", first.getSpecifics().get("oracle.scheduler.program.status"));
		}
	}

	@Test
	void missingDuplicateAndOtherErrorsDoNotPublishPartialDefinitions() {
		for (boolean duplicate : List.of(false, true)) {
			Event first = event("J1");
			assertThrows(IllegalStateException.class, () -> reader(new int[1], 0, !duplicate, duplicate)
					.setMetadataDetail(null, new ParametersContext(), List.of(first, event("J2"))));
			assertTrue(first.getDefinition().isEmpty());
			assertFalse(first.getSpecifics().containsKey("oracle.scheduler.definition"));
			assertFalse(first.getSpecifics().containsKey("oracle.scheduler.program.definition"));
		}
		assertThrows(RuntimeException.class, () -> reader(new int[1], 31600, false, false).setMetadataDetail(null,
				new ParametersContext(), List.of(event("J1"), event("J2"))));
	}

	@Test
	void emptyReadsSkipDdlAndOnlyModernResolversSelectThisReader() throws Exception {
		int[] queries = { 0 };
		reader(queries, 0, false, false).setMetadataDetail(null, new ParametersContext(), List.of());
		assertEquals(0, queries[0]);
		assertEquals(Oracle10gEventReader.class,
				DialectHolder.oracle19cDialect.getCatalogReader().getSchemaReader().getEventReader().getClass());
		assertInstanceOf(Oracle23aiEventReader.class,
				DialectHolder.oracle23aiDialect.getCatalogReader().getSchemaReader().getEventReader());
		assertInstanceOf(Oracle23aiEventReader.class,
				DialectHolder.oracle26aiDialect.getCatalogReader().getSchemaReader().getEventReader());
	}

	@Test
	void failedRefreshRemovesPreviouslyCapturedScriptsAndContexts() throws Exception {
		for (int code : new int[] { 1031, 31603, 31600 }) {
			Event first = event("J1"), second = event("J2");
			var events = List.of(first, second);
			reader(new int[1], 0, false, false).setMetadataDetail(null, new ParametersContext(), events);
			assertNotNull(first.getSpecifics().get("oracle.scheduler.definition"));
			first.setDefinition("stale legacy definition");
			if (code == 31600)
				assertThrows(RuntimeException.class, () -> reader(new int[1], code, false, false)
						.setMetadataDetail(null, new ParametersContext(), events));
			else
				reader(new int[1], code, false, false).setMetadataDetail(null, new ParametersContext(), events);
			for (Event event : events) {
				assertTrue(event.getDefinition().isEmpty());
				assertFalse(event.getSpecifics()
						.containsKey(com.sqlapp.data.db.dialect.oracle.util.OracleSchedulerDefinition.FINGERPRINT));
				for (String prefix : List.of("oracle.scheduler.", "oracle.scheduler.program.",
						"oracle.scheduler.schedule.")) {
					assertFalse(event.getSpecifics().containsKey(prefix + "definition"));
					assertFalse(event.getSpecifics().containsKey(prefix + "definitionContext"));
					assertFalse(event.getSpecifics().containsKey(prefix + "definitionOwner"));
					assertFalse(event.getSpecifics().containsKey(prefix + "definitionName"));
					assertEquals(code == 31600 ? "UNAVAILABLE" : "INACCESSIBLE",
							event.getSpecifics().get(prefix + "definitionStatus"));
				}
			}
		}
	}

	@Test
	void refreshRemovesScriptsForDependenciesNoLongerAvailable() throws Exception {
		Event first = event("J1"), second = event("J2");
		var events = List.of(first, second);
		reader(new int[1], 0, false, false).setMetadataDetail(null, new ParametersContext(), events);
		first.getSpecifics().put("oracle.scheduler.program.status", "UNAVAILABLE");
		first.getSpecifics().put("oracle.scheduler.schedule.status", "UNRESOLVED");
		reader(new int[1], 0, false, false).setMetadataDetail(null, new ParametersContext(), events);
		assertNotNull(first.getSpecifics().get("oracle.scheduler.definition"));
		for (String prefix : List.of("oracle.scheduler.program.", "oracle.scheduler.schedule."))
			for (String suffix : List.of("definition", "definitionStatus", "definitionContext", "definitionOwner",
					"definitionName"))
				assertFalse(first.getSpecifics().containsKey(prefix + suffix));
	}
}
