/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.metadata.ReaderOptions;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class InformixTriggerReaderTest {
	private static class Reader extends InformixTriggerReader {
		final List<Map<String, Object>> rows = new ArrayList<>();
		int queries;

		Reader() {
			super(DialectHolder.defaultDialect);
			setReaderOptions(new ReaderOptions());
		}

		@Override
		protected com.sqlapp.jdbc.sql.JdbcQueryHandler execute(Connection connection, SqlNode node,
				ParametersContext context, ResultSetNextHandler handler) {
			queries++;
			try {
				for (var row : rows)
					handler.handleResultSetNext(InformixMetadataReaderTest.row(row));
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
			return null;
		}
	}

	private Map<String, Object> segment(int id, String owner, String key, String text) {
		final Map<String, Object> row = new HashMap<>();
		row.put("trigger_id", id);
		row.put("catalog_name", "db");
		row.put("schema_name", owner);
		row.put("trigger_name", "tr");
		row.put("table_schema_name", "tables");
		row.put("table_name", "events");
		row.put("trigger_event", "I");
		row.put("data_key", key);
		row.put("trigger_text", text);
		return row;
	}

	@Test
	void preservesChunkWhitespaceAndCompleteDefinitionInOneQueryThroughXml() throws Exception {
		final Reader reader = new Reader();
		for (int id = 1; id <= 20; id++) {
			reader.rows.add(segment(id, "owner" + id, "D", "create trigger tr insert on events "));
			reader.rows.add(segment(id, "owner" + id, "A", "for each row (insert into audit values('BEFORE "));
			reader.rows.add(segment(id, "owner" + id, "A", "FOR EACH STATEMENT'));   "));
		}
		final var triggers = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, reader.queries);
		assertEquals(20, triggers.size());
		for (Trigger trigger : triggers) {
			assertTrue(String.join("\n", trigger.getDefinition()).contains("'BEFORE FOR EACH STATEMENT'"));
			assertNull(trigger.getActionTiming());
			assertNull(trigger.getActionOrientation());
			final Schema schema = new Schema(trigger.getSchemaName());
			schema.getTriggers().add(trigger);
			final Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
			final var sql = DialectHolder.defaultDialect.createSqlFactoryRegistry()
					.createSql(restored.getTriggers().get(0), SqlType.CREATE);
			assertEquals(2, sql.size());
			assertEquals(String.join("\n", trigger.getDefinition()), sql.get(0).getSqlText());
			assertTrue(sql.get(1).getSqlText().endsWith("ENABLED"));
		}
	}

	@Test
	void preservesDisabledStateThroughXmlAndRejectsInvalidModes() throws Exception {
		final Reader reader = new Reader();
		final var header = segment(1, "a", "D", "create trigger tr insert on events");
		header.put("trigger_state", "D");
		reader.rows.add(header);
		reader.rows.add(segment(1, "a", "A", "for each row (insert into audit values(1));"));
		final Trigger trigger = reader.doGetAll(null, new ParametersContext(), null).get(0);
		assertFalse(trigger.isEnable());
		final Trigger restored = SchemaUtils.readXml(new StringReader(trigger.asXml()));
		assertFalse(restored.isEnable());
		final var operations = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored,
				SqlType.CREATE);
		assertEquals(2, operations.size());
		assertTrue(operations.get(1).getSqlText().endsWith("DISABLED"));
		assertEquals(1, reader.queries);
		header.put("trigger_state", "F");
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void rejectsUnknownEventsAndMissingTextWithoutInventingSql() {
		final Reader reader = new Reader();
		final var row = segment(1, "a", "D", "create trigger tr");
		reader.rows.add(row);
		row.put("trigger_event", "?");
		assertThrows(UnsupportedOperationException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		row.put("trigger_event", null);
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		row.put("trigger_event", "I");
		row.put("trigger_text", null);
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void honorsTextOptionsAndRejectsIncompleteDefinitions() {
		final Reader reader = new Reader();
		reader.rows.add(segment(1, "a", "D", "create trigger tr insert on events"));
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		reader.rows.add(segment(1, "a", "A", "for each row (insert into audit values(1));"));
		reader.getReaderOptions().setReadStatement(false);
		reader.getReaderOptions().setReadDefinition(true);
		var trigger = reader.doGetAll(null, new ParametersContext(), null).get(0);
		assertNotNull(trigger.getDefinition());
		assertTrue(trigger.getStatement().isEmpty());
		reader.getReaderOptions().setReadDefinition(false);
		trigger = reader.doGetAll(null, new ParametersContext(), null).get(0);
		assertTrue(trigger.getDefinition().isEmpty());
		assertTrue(trigger.getStatement().isEmpty());
		final Trigger missing = trigger;
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(missing, SqlType.CREATE));
	}
}
