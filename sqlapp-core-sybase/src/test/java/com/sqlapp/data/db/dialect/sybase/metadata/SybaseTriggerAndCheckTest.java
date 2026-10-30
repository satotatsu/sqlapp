/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.sybase.DialectHolder;
import com.sqlapp.data.db.metadata.ReaderOptions;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SybaseTriggerAndCheckTest {
	private Map<String, Object> trigger(int id, String text) {
		var row = new HashMap<String, Object>();
		row.put("catalog_name", "db");
		row.put("schema_name", "dbo");
		row.put("trigger_name", "tr" + id);
		row.put("trigger_id", id);
		row.put("table_schema_name", "dbo");
		row.put("table_name", "items");
		row.put("definition", text);
		return row;
	}

	@Test
	void preservesTriggerTextAndParsesOnlyRealHeaderEventsThroughXmlAndCreate() throws Exception {
		final var rows = List.of(
				trigger(1,
						"/* INSTEAD OF DELETE */ CREATE TRIGGER dbo.tr1 ON dbo.items FOR INSERT, UPDATE AS SELECT 'a "),
				trigger(1, "b INSTEAD OF DELETE'"),
				trigger(2, "CREATE TRIGGER dbo.tr2 ON dbo.items INSTEAD OF DELETE AS SELECT 1"));
		final int[] calls = { 0 };
		var reader = new SybaseTriggerReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				calls[0]++;
				SybaseReaderTestSupport.feed(h, rows);
				return null;
			}
		};
		reader.setReaderOptions(new ReaderOptions());
		var triggers = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, calls[0]);
		assertEquals(2, triggers.size());
		assertEquals("STATEMENT", triggers.get(0).getActionOrientation());
		assertEquals("AFTER", triggers.get(0).getActionTiming());
		assertEquals(Set.of("INSERT", "UPDATE"), new HashSet<>(triggers.get(0).getEventManipulation()));
		assertEquals("INSTEAD OF", triggers.get(1).getActionTiming());
		final Trigger xml = SchemaUtils.readXml(new java.io.StringReader(triggers.get(0).asXml()));
		final var sql = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(xml, SqlType.CREATE).get(0)
				.getSqlText();
		assertTrue(sql.contains("'a b INSTEAD OF DELETE'"), sql);
		assertFalse(sql.contains("FOR EACH ROW"), sql);
	}

	@Test
	void allowsHeaderOnlyTriggersButRejectsHiddenRequestedText() {
		var values = trigger(1, "ciphertext");
		values.put("source_hidden", 1);
		var reader = new SybaseTriggerReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				SybaseReaderTestSupport.feed(h, List.of(values));
				return null;
			}
		};
		final var options = new ReaderOptions();
		reader.setReaderOptions(options);
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		options.setReadStatement(false);
		options.setReadDefinition(false);
		final var result = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, result.size());
		assertNull(result.get(0).getActionTiming());
		assertTrue(result.get(0).getStatement().isEmpty());
	}

	@Test
	void joinsCheckExpressionBeforeUnwrappingAndSeparatesOwners() throws Exception {
		final List<Map<String, Object>> rows = new ArrayList<>();
		for (String owner : List.of("a", "b"))
			for (String text : List.of("(value = 'a ", "b')"))
				rows.add(Map.of("catalog_name", "db", "schema_name", owner, "table_name", "items", "constraint_name",
						"shared", "definition", text));
		var reader = new SybaseCheckConstraintReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				SybaseReaderTestSupport.feed(h, rows);
				return null;
			}
		};
		final var checks = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(2, checks.size());
		for (var check : checks) {
			assertEquals("value = 'a b'", check.getExpression());
			final CheckConstraint xml = SchemaUtils.readXml(new java.io.StringReader(check.asXml()));
			assertEquals(check.getExpression(), xml.getExpression());
		}
	}
}
