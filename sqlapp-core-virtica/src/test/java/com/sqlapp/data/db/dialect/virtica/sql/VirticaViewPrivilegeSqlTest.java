/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class VirticaViewPrivilegeSqlTest {
	@Test
	void legacyResolverPathsRetainTheirExistingPrivilegeSyntax() {
		View view = new View("reports");
		view.setStatement("SELECT 1");
		view.getSpecifics().put("INHERIT_PRIVILEGES", true);
		for (int[] version : new int[][] { { 7, 1, 0 }, { 9, 0, 0 }, { 11, 1, 0 }, { 11, 1, 1 }, { 25, 1, 0 } }) {
			String sql = DialectResolver.getInstance().getDialect("Vertica", version[0], version[1], version[2])
					.createSqlFactoryRegistry().createSql(view, SqlType.CREATE).get(0).getSqlText();
			assertEquals(version[0] > 11 || version[0] == 11 && version[2] >= 1,
					sql.contains("INCLUDE SCHEMA PRIVILEGES"), sql);
		}
	}

	@Test
	void permanentViewsPreserveExplicitInheritanceThroughXml() throws Exception {
		for (boolean inherit : new boolean[] { true, false }) {
			View view = new View("reports");
			view.setStatement("SELECT 1");
			view.getSpecifics().put("INHERIT_PRIVILEGES", inherit);
			View restored = SchemaUtils.readXml(new java.io.StringReader(view.asXml()));
			var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry();
			String sql = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains((inherit ? "INCLUDE" : "EXCLUDE") + " SCHEMA PRIVILEGES\nAS"), sql);
			String full = "CREATE VIEW reports AS SELECT 2";
			restored.setDefinition(full);
			assertEquals(full, registry.createSql(restored, SqlType.CREATE).get(0).getSqlText());
		}
	}

	@Test
	void localViewsDoNotEmitSchemaQualificationOrInheritanceClauses() {
		View view = new View("session report");
		view.setSchemaName("v_temp_schema");
		view.setStatement("SELECT 1");
		view.getSpecifics().put("IS_LOCAL_TEMP_VIEW", true);
		view.getSpecifics().put("INHERIT_PRIVILEGES", false);
		String sql = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry()
				.createSql(view, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("LOCAL TEMPORARY VIEW \"session report\""), sql);
		assertFalse(sql.contains("v_temp_schema"), sql);
		assertFalse(sql.contains("SCHEMA PRIVILEGES"), sql);
	}
}
