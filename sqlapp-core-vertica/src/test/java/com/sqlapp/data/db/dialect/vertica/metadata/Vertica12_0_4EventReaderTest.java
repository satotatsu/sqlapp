/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.vertica.metadata;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.vertica.metadata.Vertica12_0_4EventReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Vertica12_0_4EventReaderTest {
	private static class Reader extends Vertica12_0_4EventReader {
		int queries;

		Reader() {
			super(DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0));
		}

		@Override
		protected JdbcQueryHandler execute(java.sql.Connection c, SqlNode n, ParametersContext p,
				ResultSetNextHandler h) {
			queries++;
			try {
				for (int i = 0; i < 4; i++) {
					final int id = i;
					h.handleResultSetNext(new ExResultSet(null) {
						@Override
						public String getString(String name) {
							return switch (name.toUpperCase(java.util.Locale.ROOT)) {
							case "TRIGGER_NAME" -> "trigger" + id;
							case "SCHEMA_NAME" -> "app";
							case "DATE_TIME_TYPE" -> id == 0 ? "CRON" : id == 1 ? "DATE_TIME_LIST" : "FUTURE_FORMAT";
							default -> null;
							};
						}

						@Override
						public boolean getBoolean(String name) {
							return name.equals("TRIGGER_ENABLED") ? id % 2 == 1 : id >= 2;
						}

						@Override
						public Object getObject(String name) {
							return switch (name) {
							case "DATE_TIME_TYPE" -> getString(name);
							case "DATE_TIME_STRING" ->
								id == 0 ? "0 1 * * *" : "2027-01-01 00:00:00, 2027-06-01 00:00:00";
							case "PROCEDURE_NAME" -> "refresh_reports";
							case "PROCEDURE_ARGS" -> "'daily'";
							case "SCHEDULE_NAME" -> "schedule" + id;
							default -> null;
							};
						}
					});
				}
			} catch (java.sql.SQLException e) {
				throw new IllegalStateException(e);
			}
			return null;
		}
	}

	@Test
	void bulkEventsRetainBothEnableStatesAndRawScheduleThroughXml() throws Exception {
		Reader reader = new Reader();
		var events = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, reader.queries);
		assertEquals(4, events.size());
		for (int i = 0; i < 4; i++) {
			Event event = SchemaUtils.readXml(new java.io.StringReader(events.get(i).asXml()));
			assertEquals(i == 3, event.isEnable());
			assertEquals(i % 2 == 1, event.getSpecifics().get("TRIGGER_ENABLED", Boolean.class));
			assertEquals(i >= 2, event.getSpecifics().get("SCHEDULE_ENABLED", Boolean.class));
			assertEquals("schedule" + i, event.getSpecifics().get("SCHEDULE_NAME"));
			assertEquals("refresh_reports", event.getSpecifics().get("PROCEDURE_NAME"));
			assertEquals("'daily'", event.getSpecifics().get("PROCEDURE_ARGS"));
			if (i == 0)
				assertEquals(EventType.Recurring, event.getEventType());
			else {
				assertNull(event.getEventType());
				assertNull(event.getExecuteAt());
				assertTrue(event.getSpecifics().get("DATE_TIME_STRING").contains("2027-06-01"));
			}
		}
	}
}
