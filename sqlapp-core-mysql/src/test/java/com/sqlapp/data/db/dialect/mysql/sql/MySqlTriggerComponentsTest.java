/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Trigger;

class MySqlTriggerComponentsTest {
	@ParameterizedTest
	@CsvSource({"5,1", "5,5", "5,7", "8,0", "8,4", "9,0"})
	void qualifiesTargetAndInfersOnlyTheKnownSchemaWithoutMutatingModel(int major, int minor) {
		var registry = DialectResolver.getInstance().getDialect("MySql", major, minor, 0).createSqlFactoryRegistry();
		Trigger trigger = trigger();
		trigger.setSchemaName("app data");
		trigger.setTableSchemaName("app data");
		trigger.setActionOrientation("row");
		trigger.setActionReferenceOldRow("OLD");
		trigger.setActionReferenceNewRow("NEW");
		SqlFactory<Trigger> factory = registry.getSqlFactory(trigger, SqlType.CREATE);
		String sql = factory.createSql(trigger).get(0).getSqlText();
		assertTrue(sql.contains("TRIGGER `app data`.`before insert`"), sql);
		assertTrue(sql.contains("ON `app data`.`order details`"), sql);
		assertTrue(sql.contains("FOR EACH ROW"), sql);
		assertTrue(sql.endsWith("SET NEW.id = 1"), sql);
		trigger.setSchemaName(null);
		sql = factory.createSql(trigger).get(0).getSqlText();
		assertTrue(sql.contains("TRIGGER `app data`.`before insert`"), sql);
		assertTrue(sql.contains("ON `app data`.`order details`"), sql);
		assertNull(trigger.getSchemaName());
		trigger.setSchemaName("app data");
		trigger.setTableSchemaName(null);
		sql = factory.createSql(trigger).get(0).getSqlText();
		assertTrue(sql.contains("ON `app data`.`order details`"), sql);
		factory.getOptions().setDecorateSchemaName(false);
		sql = factory.createSql(trigger).get(0).getSqlText();
		assertTrue(sql.contains("TRIGGER `before insert`"), sql);
		assertTrue(sql.contains("ON `app data`.`order details`"), sql);
	}

	@ParameterizedTest
	@CsvSource({"5,1", "5,5", "5,7", "8,0", "8,4", "9,0"})
	void invalidComponentsFailAndCompleteDefinitionKeepsPrecedence(int major, int minor) {
		var registry = DialectResolver.getInstance().getDialect("MySql", major, minor, 0).createSqlFactoryRegistry();
		List<Consumer<Trigger>> invalid = List.of(t -> t.getTable().setName(null), t -> t.setTableName(" "),
				t -> t.setName(" "), t -> t.setStatement(List.of()), t -> t.setStatement(" \n\t"),
				t -> t.setActionOrientation("STATEMENT"), t -> t.setWhen("NEW.id > 0"),
				t -> t.setActionCondition("NEW.id > 0"), t -> t.setEnable(false),
				t -> t.setActionReferenceOldRow("old_row"), t -> t.setActionReferenceNewRow("new_row"),
				t -> {t.setSchemaName("app"); t.setTableSchemaName("other");});
		for (Consumer<Trigger> change : invalid) {
			Trigger trigger = trigger();
			change.accept(trigger);
			SqlFactory<Trigger> factory = registry.getSqlFactory(trigger, SqlType.CREATE);
			assertThrows(IllegalArgumentException.class, () -> factory.createSql(trigger), "invalid case " + invalid.indexOf(change));
			trigger.setDefinition("CREATE TRIGGER complete_definition");
			assertEquals("CREATE TRIGGER complete_definition", factory.createSql(trigger).get(0).getSqlText());
		}
	}

	private Trigger trigger() {
		Trigger trigger = new Trigger("before insert");
		trigger.setTableName("order details");
		trigger.setActionTiming("BEFORE");
		trigger.addEventManipulation("INSERT");
		trigger.setStatement("SET NEW.id = 1");
		return trigger;
	}
}
