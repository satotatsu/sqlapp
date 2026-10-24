/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.jdbc.ExResultSet;

class FirebirdSecurityMetadataTest {
	@Test
	void preservesExplicitTableSecurityAndLeavesInheritanceUnset() throws Exception {
		var reader = new Firebird40TableReader(DialectHolder.defaultDialect40);
		for (Boolean security : new Boolean[] { null, Boolean.FALSE, Boolean.TRUE }) {
			var values = new java.util.HashMap<String, Object>();
			values.put("RDB$RELATION_NAME", "secured");
			values.put("RDB$SQL_SECURITY", security);
			Table table = reader.createTable(row(values));
			assertEquals(security == null ? null : security ? "DEFINER" : "INVOKER",
					table.getSpecifics().get("firebird.sqlSecurity"));
		}
	}

	@Test
	void legacyTriggerReaderDoesNotAccessNewVersionProperties() throws Exception {
		var row = new ExResultSet(null) {
			@Override
			public String getString(String key) {
				throw new AssertionError("Unexpected legacy catalog access: " + key);
			}

			@Override
			public Object getObject(String key) {
				throw new AssertionError("Unexpected legacy catalog access: " + key);
			}
		};
		new FirebirdTriggerReader(DialectHolder.defaultDialect25).readVersionProperties(row, new Trigger("legacy"));
	}

	@Test
	void recreatesExternalTriggersAndValidatesEngineVersion() throws Exception {
		Trigger trigger = new Trigger("external_audit");
		trigger.setTableName("orders").setActionTiming("AFTER").setEventManipulation(new String[] { "INSERT" });
		new Firebird30TriggerReader(DialectHolder.defaultDialect30).readVersionProperties(
				row(Map.of("RDB$ENGINE_NAME", "UDR", "RDB$ENTRYPOINT", "example!audit")), trigger);
		String ddl = DialectHolder.defaultDialect30.createSqlFactoryRegistry().createSql(trigger, SqlType.CREATE).get(0)
				.getSqlText();
		assertTrue(ddl.contains("EXTERNAL NAME 'example!audit' ENGINE UDR"), ddl);
		assertFalse(ddl.contains("BEGIN"), ddl);
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(trigger, SqlType.CREATE));
	}

	@Test
	void emitsTriggerSecurityOnlyAtTheSupportedBoundary() throws Exception {
		Trigger trigger = new Trigger("secured_trigger");
		trigger.setEventManipulation(new String[] { "ON CONNECT" }).setStatement("BEGIN END");
		new Firebird40TriggerReader(DialectHolder.defaultDialect40)
				.readVersionProperties(row(Map.of("RDB$SQL_SECURITY", true)), trigger);
		String ddl = DialectHolder.defaultDialect40.createSqlFactoryRegistry().createSql(trigger, SqlType.CREATE).get(0)
				.getSqlText();
		assertTrue(ddl.contains("SQL SECURITY DEFINER"), ddl);
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.defaultDialect30.createSqlFactoryRegistry().createSql(trigger, SqlType.CREATE));
		trigger.getSpecifics().put("firebird.sqlSecurity", "invalid");
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.defaultDialect40.createSqlFactoryRegistry().createSql(trigger, SqlType.CREATE));
	}

	private ExResultSet row(Map<String, Object> values) {
		return new ExResultSet(null) {
			@Override
			public String getString(String key) {
				return values.get(key) == null ? null : values.get(key).toString();
			}

			@Override
			public Object getObject(String key) {
				return values.get(key);
			}

			@Override
			public boolean getBoolean(String key) {
				return Boolean.TRUE.equals(values.get(key));
			}
		};
	}
}
