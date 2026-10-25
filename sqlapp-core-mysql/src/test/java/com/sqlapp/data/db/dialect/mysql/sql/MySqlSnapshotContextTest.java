/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.mysql.util.MySqlTriggerOrder;
import com.sqlapp.data.db.dialect.mysql.util.MySqlStoredObjectContext;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;

class MySqlSnapshotContextTest {
	@Test
	void completeTableDefinitionOverridesSupplementalComponents() {
		var registry = DialectResolver.getInstance().getDialect("MySql", 8, 4, 0).createSqlFactoryRegistry();
		Table table = new Table("t");
		table.setDefinition("CREATE TABLE t(id INT) ENGINE=InnoDB ROW_FORMAT=DYNAMIC");
		table.getSpecifics().put("ENGINE", "invalid supplemental setting");
		SqlFactory<Table> factory = registry.getSqlFactory(table, SqlType.CREATE);
		assertEquals(1, factory.createSql(table).size());
		assertEquals(table.getDefinition().get(0), factory.createSql(table).get(0).getSqlText());
	}

	@Test
	void collectionAndSchemaPathsPreserveNativeTriggerOrder() {
		var registry = DialectResolver.getInstance().getDialect("MySql", 8, 4, 0).createSqlFactoryRegistry();
		Trigger first = trigger("z_first", "1"), second = trigger("a_second", "2");
		SqlFactory<Trigger> factory = registry.getSqlFactory(first, SqlType.CREATE);
		var sql = factory.createSql(List.of(second, first));
		assertTrue(sql.get(0).getSqlText().contains("z_first"));
		assertTrue(sql.get(1).getSqlText().contains("a_second"));
		Schema schema = new Schema("app");
		schema.getTriggers().add(second);
		schema.getTriggers().add(first);
		SqlFactory<Schema> schemaFactory = registry.getSqlFactory(schema, SqlType.CREATE);
		var operations = schemaFactory.createSql(schema);
		assertTrue(operations.get(1).getSqlText().contains("z_first"));
		assertTrue(operations.get(2).getSqlText().contains("a_second"));
		first.getSpecifics().put(MySqlTriggerOrder.ACTION_ORDER, "0");
		assertEquals(List.of(first), MySqlTriggerOrder.sort(List.of(first)));
		assertThrows(IllegalArgumentException.class, () -> MySqlTriggerOrder.sort(List.of(second, first)));
		first.getSpecifics().put(MySqlTriggerOrder.ACTION_ORDER, "2");
		assertThrows(IllegalArgumentException.class, () -> MySqlTriggerOrder.sort(List.of(second, first)));
		first.getSpecifics().remove(MySqlTriggerOrder.ACTION_ORDER);
		assertThrows(IllegalArgumentException.class, () -> MySqlTriggerOrder.sort(List.of(second, first)));
		first.getSpecifics().put(MySqlTriggerOrder.ACTION_ORDER, "-1");
		assertThrows(IllegalArgumentException.class, () -> MySqlTriggerOrder.order(first));
	}

	@Test
	void triggerOrderGroupsSqlKeywordsWithoutCaseSensitivity() {
		Trigger first = trigger("z_first", "1"), second = trigger("a_second", "2");
		first.getEventManipulation().clear();
		first.addEventManipulation("insert");
		first.setActionTiming("before");
		assertEquals(List.of(first, second), MySqlTriggerOrder.sort(List.of(second, first)));
		first.getSpecifics().put(MySqlTriggerOrder.ACTION_ORDER, "2");
		assertThrows(IllegalArgumentException.class, () -> MySqlTriggerOrder.sort(List.of(second, first)));
	}

	@Test
	void optionalContextColumnsAreRetainedWithoutAdditionalQueries() throws Exception {
		try (var rows = javax.sql.rowset.RowSetProvider.newFactory().createCachedRowSet()) {
			var metadata = new javax.sql.rowset.RowSetMetaDataImpl();
			metadata.setColumnCount(2);
			metadata.setColumnName(1, "SQL_MODE");
			metadata.setColumnType(1, java.sql.Types.VARCHAR);
			metadata.setColumnName(2, "TIME_ZONE");
			metadata.setColumnType(2, java.sql.Types.VARCHAR);
			rows.setMetaData(metadata);
			rows.moveToInsertRow();
			rows.updateString(1, "");
			rows.updateString(2, "+09:00");
			rows.insertRow();
			rows.moveToCurrentRow();
			rows.beforeFirst();
			assertTrue(rows.next());
			Trigger trigger = trigger("t", "1");
			MySqlStoredObjectContext.read(rows, trigger);
			assertEquals("", trigger.getSpecifics().get(MySqlStoredObjectContext.SQL_MODE));
			assertEquals("+09:00", trigger.getSpecifics().get(MySqlStoredObjectContext.TIME_ZONE));
		}
	}

	private Trigger trigger(String name, String rank) {
		Trigger trigger = new Trigger(name);
		trigger.setTableName("t");
		trigger.setActionTiming("BEFORE");
		trigger.addEventManipulation("INSERT");
		trigger.setStatement("SET NEW.id=1");
		trigger.getSpecifics().put(MySqlTriggerOrder.ACTION_ORDER, rank);
		return trigger;
	}
}
