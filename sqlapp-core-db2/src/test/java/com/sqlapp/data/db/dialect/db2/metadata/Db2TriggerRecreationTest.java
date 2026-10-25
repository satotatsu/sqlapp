/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2TriggerRecreationTest {
	private Map<String, String> fields() {
		var values = new HashMap<String, String>();
		values.putAll(Map.of("trigger_name", "TR\".Q", "schema_name", "S.Q", "EVENT_OBJECT_SCHEMA", "APP",
				"EVENT_OBJECT_TABLE", "T", "ACTION_TIMING", "AFTER", "ACTION_ORIENTATION", "ROW", "EVENT_MANIPULATION",
				"", "TRIGEVENT", "M", "VALID", "X"));
		values.putAll(Map.of("EVENTINSERT", "Y", "EVENTUPDATE", "Y", "EVENTDELETE", "N", "QUALIFIER", "Base.Q",
				"FUNC_PATH", "\"SYSIBM\",\"Fns.Q\"", "remarks", "trigger's comment", "ACTION_STATEMENT",
				"CREATE TRIGGER \"S.Q\".\"TR\"\".Q\" AFTER INSERT OR UPDATE ON APP.T\nFOR EACH ROW\nBEGIN ATOMIC\n\n VALUES 'a;b';\nEND"));
		return values;
	}

	private List<String> sql(Trigger trigger, SqlType type) {
		SqlFactory<Trigger> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(trigger,
				type);
		return factory.createSql(trigger).stream().map(SqlOperation::getSqlText).toList();
	}

	@Test
	void inactiveMultieventTriggerRetainsNativeDdlContextAndCommentsAfterXml() throws Exception {
		var values = fields();
		Trigger trigger = new Db2TriggerReader(DialectHolder.Db2_1110Dialect)
				.createTrigger(Db2MetadataParityTest.row(values));
		assertEquals(Set.of("INSERT", "UPDATE"), trigger.getEventManipulation());
		assertFalse(trigger.isValid());
		assertEquals("X", trigger.getSpecifics().get("VALID"));
		assertEquals("APP", trigger.getTableSchemaName());
		assertEquals("T", trigger.getTableName());
		Trigger restored = SchemaUtils.readXml(new StringReader(trigger.asXml()));
		assertEquals(trigger, restored);
		assertEquals(trigger, trigger.clone());
		assertEquals(List.of("SET CURRENT SCHEMA = 'Base.Q'", "SET CURRENT PATH = \"SYSIBM\", \"Fns.Q\"",
				values.get("ACTION_STATEMENT"), "COMMENT ON TRIGGER \"S.Q\".\"TR\"\".Q\" IS 'trigger''s comment'"),
				sql(restored, SqlType.CREATE));
		assertEquals(List.of("DROP TRIGGER \"S.Q\".\"TR\"\".Q\""), sql(restored, SqlType.DROP));
	}

	@Test
	void singleEventLegacyContextAndHistoricalQueryRemainSeparate() throws Exception {
		var values = fields();
		values.put("TRIGEVENT", "D");
		values.put("EVENT_MANIPULATION", "DELETE");
		values.remove("EVENTINSERT");
		values.remove("EVENTUPDATE");
		var modern = new Db2TriggerReader(DialectHolder.Db2_1110Dialect);
		assertEquals(Set.of("DELETE"), modern.createTrigger(Db2MetadataParityTest.row(values)).getEventManipulation());
		var old = new Db2TriggerReader(DialectHolder.Db2_1050Dialect);
		assertFalse(old.createTrigger(Db2MetadataParityTest.row(values)).getSpecifics().containsKey("QUALIFIER"));
		assertTrue(old.getSqlNode(new ProductVersionInfo()).toString().contains("tr.VALID = 'Y'"));
		assertFalse(modern.getSqlNode(new ProductVersionInfo()).toString().contains("tr.VALID = 'Y'"));
	}

	@Test
	void twentyTriggersIncludingInvalidOnesUseOneBulkQuery() {
		AtomicInteger count = new AtomicInteger();
		var reader = new Db2TriggerReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode sql, ParametersContext p,
					ResultSetNextHandler handler) {
				count.incrementAndGet();
				for (int i = 0; i < 20; i++)
					try {
						var values = fields();
						values.put("trigger_name", "TR" + i);
						values.put("VALID", i % 2 == 0 ? "Y" : "N");
						handler.handleResultSetNext(Db2MetadataParityTest.row(values));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				return null;
			}
		};
		var triggers = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(20, triggers.size());
		assertEquals(1, count.get());
		assertEquals(10, triggers.stream().filter(Trigger::isValid).count());
	}

	@Test
	void incompleteInvalidAndImplicitReplacementRequestsFailClearly() {
		Trigger trigger = new Trigger("TR").setSchemaName("APP").setStatement("VALUES 1");
		assertThrows(IllegalArgumentException.class, () -> sql(trigger, SqlType.CREATE));
		trigger.setDefinition("CREATE TRIGGER APP.TR AFTER INSERT ON APP.T FOR EACH ROW VALUES 1");
		trigger.getSpecifics().put("FUNC_PATH", "\"unterminated");
		assertThrows(IllegalArgumentException.class, () -> sql(trigger, SqlType.CREATE));
		trigger.getSpecifics().clear();
		Trigger changed = trigger.clone()
				.setDefinition("CREATE TRIGGER APP.TR AFTER INSERT ON APP.T FOR EACH ROW VALUES 2");
		SqlFactory<Trigger> alter = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(changed,
				SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(trigger.diff(changed)));
		Schema before = new Schema("APP"), after = new Schema("APP");
		before.getTriggers().add(trigger);
		after.getTriggers().add(changed);
		SqlFactory<Schema> schemaAlter = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(after,
				SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> schemaAlter.createDiffSql(before.diff(after)));
		trigger.setDefinition("\0");
		assertThrows(IllegalArgumentException.class, () -> sql(trigger, SqlType.CREATE));
	}

	@Test
	void schemaReplayPreservesCreationOrderAfterXmlAndManualOrderWithoutTimestamps() throws Exception {
		Schema schema = new Schema("APP");
		Trigger later = new Trigger("A")
				.setDefinition("CREATE TRIGGER APP.A AFTER INSERT ON APP.T FOR EACH ROW VALUES 2")
				.setCreatedAt(Timestamp.valueOf("2026-01-02 00:00:00"));
		Trigger earlier = new Trigger("Z")
				.setDefinition("CREATE TRIGGER APP.Z AFTER INSERT ON APP.T FOR EACH ROW VALUES 1")
				.setCreatedAt(Timestamp.valueOf("2026-01-01 00:00:00"));
		schema.getTriggers().add(later);
		schema.getTriggers().add(earlier);
		Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
		SqlFactory<Schema> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(restored,
				SqlType.CREATE);
		var sql = factory.createSql(restored).stream().map(SqlOperation::getSqlText)
				.filter(s -> s.startsWith("CREATE TRIGGER")).toList();
		assertEquals(List.of(String.join("\n", earlier.getDefinition()), String.join("\n", later.getDefinition())),
				sql);
		later.setCreatedAt(null);
		earlier.setCreatedAt(null);
		SqlFactory<Trigger> triggers = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(later,
				SqlType.CREATE);
		assertTrue(triggers.createSql(List.of(later, earlier)).getFirst().getSqlText().contains("APP.A"));
	}
}
