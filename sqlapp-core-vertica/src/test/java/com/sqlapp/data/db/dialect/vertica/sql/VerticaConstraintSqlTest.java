/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.vertica.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class VerticaConstraintSqlTest {
	@Test
	void constraintEnforcementSurvivesXmlAndBothCreatePaths() throws Exception {
		for (int major : new int[] { 7, 8, 9, 11, 12, 25 }) {
			var registry = DialectResolver.getInstance().getDialect("Vertica", major, major == 7 ? 2 : 0, 0)
					.createSqlFactoryRegistry();
			for (boolean enabled : new boolean[] { true, false }) {
				Table table = new Table("items");
				table.getColumns().add("id", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT));
				for (boolean primary : new boolean[] { true, false }) {
					UniqueConstraint key = new UniqueConstraint(primary ? "pk" : "uq", primary);
					key.getColumns().add("id");
					key.setEnable(enabled);
					table.getConstraints().add(key);
				}
				CheckConstraint check = new CheckConstraint("positive", "id > 0");
				check.setEnable(enabled);
				table.getConstraints().add(check);
				Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
				String suffix = enabled ? " ENABLED" : " DISABLED";
				String ddl = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
				assertEquals(3, ddl.split(suffix, -1).length - 1, ddl);
				for (var constraint : restored.getConstraints()) {
					String sql = registry.createSql(constraint, SqlType.CREATE).get(0).getSqlText();
					assertTrue(sql.endsWith(suffix), sql);
				}
			}
		}
	}

	@Test
	void missingCheckExpressionFailsInsteadOfGeneratingEmptyCheck() {
		var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry();
		Table table = new Table("items");
		CheckConstraint check = new CheckConstraint("unknown", (String) null);
		table.getConstraints().add(check);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(check, SqlType.CREATE));
		check.setExpression("  ");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
	}

	@Test
	void pre72KeepsLegacyKeySyntaxAndRejectsChecks() {
		var registry = DialectResolver.getInstance().getDialect("Vertica", 7, 1, 0).createSqlFactoryRegistry();
		Table table = new Table("items");
		UniqueConstraint key = new UniqueConstraint("pk", true);
		key.getColumns().add("id");
		table.getConstraints().add(key);
		String sql = registry.createSql(key, SqlType.CREATE).get(0).getSqlText();
		assertFalse(sql.contains("ENABLED"), sql);
		assertFalse(sql.contains("DISABLED"), sql);
		CheckConstraint check = new CheckConstraint("positive", "id > 0");
		table.getConstraints().add(check);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(check, SqlType.CREATE));
	}
}
