/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.View;

class PostgresViewOptionsTest {
	@Test
	void respectsOptionVersionBoundaries() {
		for (int[] version : new int[][] { { 9, 1 }, { 9, 2 }, { 9, 3 }, { 9, 4 }, { 11, 0 }, { 14, 0 }, { 15, 0 },
				{ 18, 0 } }) {
			var registry = DialectResolver.getInstance().getDialect("postgres", version[0], version[1], null)
					.createSqlFactoryRegistry();
			for (String name : java.util.List.of("security_barrier", "check_option", "security_invoker")) {
				var view = new View("visible");
				view.setStatement("SELECT 1 AS id");
				view.getSpecifics().put(name, "check_option".equals(name) ? "LOCAL" : "true");
				boolean supported = "security_invoker".equals(name) ? version[0] >= 15
						: version[0] > 9 || version[1] >= ("check_option".equals(name) ? 4 : 2);
				if (supported) {
					String sql = registry.createSql(view, SqlType.CREATE).get(0).getSqlText();
					assertTrue(sql.contains(name + "=" + ("check_option".equals(name) ? "'local'" : "true")), sql);
					assertTrue(sql.indexOf("WITH (") < sql.indexOf("SELECT"), sql);
				} else
					assertThrows(IllegalArgumentException.class, () -> registry.createSql(view, SqlType.CREATE));
			}
		}
	}

	@Test
	void validatesOptionsAndKeepsCompleteDefinitionAuthoritative() {
		var registry = DialectResolver.getInstance().getDialect("postgres", 15, 0, null).createSqlFactoryRegistry();
		var view = new View("visible");
		view.setStatement("SELECT 1 AS id");
		assertFalse(registry.createSql(view, SqlType.CREATE).get(0).getSqlText().contains("WITH ("));
		view.getSpecifics().put("security_barrier", "FALSE");
		view.getSpecifics().put("security_invoker", " true ");
		view.getSpecifics().put("check_option", "CASCADED");
		String configured = registry.createSql(view, SqlType.CREATE).get(0).getSqlText();
		assertTrue(configured.contains("security_barrier=false, check_option='cascaded', security_invoker=true"),
				configured);
		for (String name : java.util.List.of("security_barrier", "check_option", "security_invoker")) {
			view.getSpecifics().clear();
			view.getSpecifics().put(name, "true); DROP TABLE items; --");
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(view, SqlType.CREATE));
		}
		view.setDefinition("CREATE VIEW visible AS SELECT 2 AS id");
		assertEquals("CREATE VIEW visible AS SELECT 2 AS id",
				registry.createSql(view, SqlType.CREATE).get(0).getSqlText());
	}
}
