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
	private Event event(String name) {
		Event event = new Event(name).setSchemaName("APP").setEnable(false);
		event.setStatement("BEGIN NULL; END;");
		for (String kind : List.of("PROGRAM", "SCHEDULE")) {
			event.getSpecifics().put("oracle.scheduler." + kind + "_OWNER", "SHARED");
			event.getSpecifics().put("oracle.scheduler." + kind + "_NAME", kind);
			event.getSpecifics().put("oracle.scheduler." + kind.toLowerCase(java.util.Locale.ROOT) + ".status", "AVAILABLE");
		}
		return event;
	}

	private String script(String kind, String name) {
		return "BEGIN\n dbms_scheduler.create_" + kind.toLowerCase(java.util.Locale.ROOT) + "('" + name + "');\n"
				+ " -- 日本語 'quoted'\n dbms_scheduler.enable('" + name + "');\nEND;\n/\n";
	}

	private Oracle23aiEventReader reader(int[] queries, int deniedCode, boolean missing, boolean duplicate) {
		return new Oracle23aiEventReader(DialectHolder.oracle26aiDialect) {
			@Override protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				queries[0]++;
				try {
					for (String[] object : new String[][] { { "JOB", "APP", "J1" }, { "JOB", "APP", "J2" },
							{ "PROGRAM", "SHARED", "PROGRAM" }, { "SCHEDULE", "SHARED", "SCHEDULE" } }) {
						if (object[2].equals("J2")) {
							if (deniedCode != 0) throw new SQLException("metadata error", "", deniedCode);
							if (missing) continue;
						}
						ExResultSet row = new ExResultSet(null) {
							@Override public String getString(String key) {
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
						if (duplicate) h.handleResultSetNext(row);
					}
				} catch (SQLException error) { throw new RuntimeException(error); }
				return null;
			}
		};
	}

	@Test void capturesTheCompleteSharedBatchAndPreservesScriptsWithoutRewritingOrExecuting() throws Exception {
		Event first = event("J1"), second = event("J2");
		int[] queries = { 0 };
		reader(queries, 0, false, false).setMetadataDetail(null, new ParametersContext(), List.of(first, second));
		assertEquals(1, queries[0]);
		Event restored = SchemaUtils.readXml(new java.io.StringReader(first.asXml()));
		assertEquals(com.sqlapp.util.CommonUtils.splitLine(script("JOB", "J1")), restored.getDefinition());
		assertEquals("AVAILABLE", restored.getSpecifics().get("oracle.scheduler.definitionStatus"));
		assertEquals("SOURCE_OWNER", restored.getSpecifics().get("oracle.scheduler.definitionContext"));
		assertEquals(script("PROGRAM", "PROGRAM"), restored.getSpecifics().get("oracle.scheduler.program.definition"));
		assertEquals(script("SCHEDULE", "SCHEDULE"), restored.getSpecifics().get("oracle.scheduler.schedule.definition"));
		assertEquals(first.getSpecifics().get("oracle.scheduler.program.definition"), second.getSpecifics().get("oracle.scheduler.program.definition"));
		assertFalse(restored.isEnable());
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE));
	}

	@Test void deniedMetadataDiscardsTheWholePartialBatchButRetainsInventory() throws Exception {
		for (int code : new int[] { 1031, 31603 }) {
			Event first = event("J1"), second = event("J2");
			reader(new int[1], code, false, false).setMetadataDetail(null, new ParametersContext(), List.of(first, second));
			assertTrue(first.getDefinition().isEmpty());
			assertTrue(second.getDefinition().isEmpty());
			assertEquals("INACCESSIBLE", first.getSpecifics().get("oracle.scheduler.definitionStatus"));
			assertEquals("INACCESSIBLE", first.getSpecifics().get("oracle.scheduler.program.definitionStatus"));
			assertEquals("AVAILABLE", first.getSpecifics().get("oracle.scheduler.program.status"));
		}
	}

	@Test void missingDuplicateAndOtherErrorsDoNotPublishPartialDefinitions() {
		for (boolean duplicate : List.of(false, true)) {
			Event first = event("J1");
			assertThrows(IllegalStateException.class, () -> reader(new int[1], 0, !duplicate, duplicate)
					.setMetadataDetail(null, new ParametersContext(), List.of(first, event("J2"))));
			assertTrue(first.getDefinition().isEmpty());
			assertFalse(first.getSpecifics().containsKey("oracle.scheduler.program.definition"));
		}
		assertThrows(RuntimeException.class, () -> reader(new int[1], 31600, false, false)
				.setMetadataDetail(null, new ParametersContext(), List.of(event("J1"), event("J2"))));
	}

	@Test void emptyReadsSkipDdlAndOnlyModernResolversSelectThisReader() throws Exception {
		int[] queries = { 0 };
		reader(queries, 0, false, false).setMetadataDetail(null, new ParametersContext(), List.of());
		assertEquals(0, queries[0]);
		assertEquals(Oracle10gEventReader.class, DialectHolder.oracle19cDialect.getCatalogReader().getSchemaReader().getEventReader().getClass());
		assertInstanceOf(Oracle23aiEventReader.class, DialectHolder.oracle23aiDialect.getCatalogReader().getSchemaReader().getEventReader());
		assertInstanceOf(Oracle23aiEventReader.class, DialectHolder.oracle26aiDialect.getCatalogReader().getSchemaReader().getEventReader());
	}
}
