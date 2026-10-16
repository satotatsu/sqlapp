/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.ExResultSet;

class OracleTriggerRecreationTest {
	@Test
	void catalogHeaderAndBodySurviveRecreation() throws Exception {
		for (String type : new String[] { "BEFORE EACH ROW", "AFTER STATEMENT", "INSTEAD OF", "COMPOUND" }) {
			String header = "\"APP\".\"TRG\" " + switch (type) {
			case "AFTER STATEMENT" -> "AFTER UPDATE ON APP.T";
			case "INSTEAD OF" -> "INSTEAD OF UPDATE ON APP.V FOR EACH ROW";
			case "COMPOUND" -> "FOR UPDATE ON APP.T";
			default -> "BEFORE UPDATE OF AMOUNT ON APP.T REFERENCING NEW AS N FOR EACH ROW";
			};
			String condition = type.equals("BEFORE EACH ROW") ? "N.AMOUNT > 0" : "";
			String body = type.equals("COMPOUND")
					? "COMPOUND TRIGGER\nAFTER STATEMENT IS BEGIN NULL; END AFTER STATEMENT;\nEND TRG;"
					: "BEGIN NULL; END;";
			Map<String, String> values = Map.of("OWNER", "APP", "TRIGGER_NAME", "TRG", "TRIGGER_TYPE", type, "STATUS",
					"DISABLED", "TABLE_OWNER", "APP", "TABLE_NAME", "T", "TRIGGERING_EVENT", "UPDATE", "DESCRIPTION",
					header, "TRIGGER_BODY", body, "WHEN_CLAUSE", condition);
			Trigger trigger = new OracleTriggerReader(DialectHolder.oracle19cDialect)
					.createTrigger(new ExResultSet(null) {
						@Override
						public String getString(String column) {
							return values.get(column);
						}
					});
			assertEquals("APP", trigger.getSchemaName());
			assertFalse(trigger.isEnable());
			if (type.equals("INSTEAD OF"))
				assertEquals("INSTEAD OF", trigger.getActionTiming());
			var operations = DialectHolder.oracle19cDialect.createSqlFactoryRegistry().createSql(trigger,
					SqlType.CREATE);
			assertEquals(2, operations.size());
			String when = condition.isEmpty() ? "" : "\nWHEN (" + condition + ")";
			assertEquals("CREATE OR REPLACE TRIGGER " + header + when + "\n" + body, operations.get(0).getSqlText());
			assertTrue(operations.get(1).getSqlText().contains("DISABLE"));
			Trigger restored = SchemaUtils.readXml(new java.io.StringReader(trigger.asXml()));
			var restoredSql = DialectHolder.oracle19cDialect.createSqlFactoryRegistry().createSql(restored,
					SqlType.CREATE);
			assertEquals(operations.get(0).getSqlText(), restoredSql.get(0).getSqlText());
			assertEquals(operations.get(1).getSqlText(), restoredSql.get(1).getSqlText());
		}
	}

	@Test
	void modeledStatementTriggerDoesNotUseForEachStatement() {
		Trigger trigger = new Trigger("TRG");
		trigger.setTableName("T");
		trigger.setActionTiming("AFTER");
		trigger.setActionOrientation("STATEMENT");
		trigger.addEventManipulation("UPDATE");
		trigger.setStatement("BEGIN NULL; END;");
		String sql = DialectHolder.oracle19cDialect.createSqlFactoryRegistry().createSql(trigger, SqlType.CREATE).get(0)
				.getSqlText();
		assertFalse(sql.contains("FOR EACH"));
		assertTrue(sql.contains("AFTER"));
	}

	@Test
	void modeledRowTriggerRetainsCondition() {
		Trigger trigger = new Trigger("TRG");
		trigger.setTableName("T");
		trigger.setActionTiming("BEFORE");
		trigger.setActionOrientation("ROW");
		trigger.addEventManipulation("INSERT");
		trigger.setActionCondition("NEW.AMOUNT > 0");
		trigger.setStatement("BEGIN NULL; END;");
		String sql = DialectHolder.oracle19cDialect.createSqlFactoryRegistry().createSql(trigger, SqlType.CREATE).get(0)
				.getSqlText();
		assertTrue(sql.contains("FOR EACH ROW"));
		assertTrue(sql.contains("WHEN (NEW.AMOUNT > 0)"));
	}
}
