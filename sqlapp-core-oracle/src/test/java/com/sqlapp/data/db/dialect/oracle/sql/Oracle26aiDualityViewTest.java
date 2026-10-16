/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.View;

class Oracle26aiDualityViewTest {
	private View view() {
		View view = new View("DV");
		view.setSchemaName("APP");
		view.setStatement("SELECT JSON {'_id': t.ID} FROM T t");
		return OracleJsonDualityViewUtils.setJsonRelationalDualityView(view, true);
	}

	@Test
	void replicationSurvivesXmlAndCreatesAt26ai() throws Exception {
		for (boolean enabled : new boolean[] { true, false }) {
			View view = view();
			view.getSpecifics().put(OracleJsonDualityViewUtils.LOGICAL_REPLICATION, enabled);
			View restored = SchemaUtils.readXml(new StringReader(view.asXml()));
			String sql = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE)
					.get(0).getSqlText().replaceAll("\\s+", " ");
			assertTrue(sql.contains("AS " + (enabled ? "ENABLE" : "DISABLE") + " LOGICAL REPLICATION SELECT"), sql);
			assertTrue(sql.contains("APP.DV"), sql);
			assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle23aiDialect
					.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE));
		}
	}

	@Test
	void validatesSettingsAndPreservesFullDefinition() {
		View view = view();
		String definition = "CREATE JSON RELATIONAL DUALITY VIEW APP.DV AS SELECT JSON {'_id': ID} FROM T";
		view.setDefinition(definition);
		assertEquals(definition, DialectHolder.oracle26aiDialect.createSqlFactoryRegistry()
				.createSql(view, SqlType.CREATE).get(0).getSqlText());
		view.getSpecifics().put(OracleJsonDualityViewUtils.LOGICAL_REPLICATION, true);
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(view, SqlType.CREATE));
		view.setDefinition((String) null);
		view.getSpecifics().put(OracleJsonDualityViewUtils.LOGICAL_REPLICATION, "invalid");
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(view, SqlType.CREATE));
		view.getSpecifics().put(OracleJsonDualityViewUtils.LOGICAL_REPLICATION, false);
		OracleJsonDualityViewUtils.setJsonRelationalDualityView(view, false);
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(view, SqlType.CREATE));
	}
}
