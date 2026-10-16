/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Mview;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.ExResultSet;

class OracleMviewRecreationTest {
	private Mview view() {
		Mview view = new Mview("MV");
		view.setSchemaName("APP").setStatement("SELECT ID FROM APP.T");
		return view;
	}
	private String sql(Mview view, SqlType type) {
		return DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(view, type).get(0).getSqlText().replaceAll("\\s+", " ");
	}
	@Test
	void rewriteAndIndexSettingsSurviveCatalogReadingAndXml() throws Exception {
		for (String flag : List.of("Y", "N")) {
			var values = Map.of("MVIEW_NAME", "MV", "OWNER", "APP", "QUERY", "SELECT ID FROM APP.T",
					"REWRITE_ENABLED", flag, "USE_NO_INDEX", flag, "COMPILE_STATE", "NEEDS_COMPILE");
			Mview view = new OracleMviewReader(DialectHolder.defaultDialect).createTable(new ExResultSet(null) {
				@Override public String getString(String key) { return values.get(key); }
				@Override public Object getObject(String key) { return values.get(key); }
			});
			Mview restored = SchemaUtils.readXml(new java.io.StringReader(view.asXml()));
			String generated = sql(restored, SqlType.CREATE);
			assertTrue(generated.contains("Y".equals(flag) ? "ENABLE QUERY REWRITE AS" : "DISABLE QUERY REWRITE AS"));
			assertEquals("Y".equals(flag), generated.contains("USING NO INDEX"));
			assertFalse(restored.isValid()); assertEquals("NEEDS_COMPILE", restored.getSpecifics().get("COMPILE_STATE"));
		}
		for (String key : List.of("REWRITE_ENABLED", "USE_NO_INDEX")) {
			Mview invalid = view(); invalid.getSpecifics().put(key, "invalid");
			assertThrows(IllegalArgumentException.class, () -> sql(invalid, SqlType.CREATE));
		}
	}
	@Test
	void metadataOptionsSurviveXmlAndRecreation() throws Exception {
		var values = Map.of("MVIEW_NAME", "MV", "OWNER", "APP", "QUERY", "SELECT ID FROM APP.T",
				"BUILD_MODE", "DEFERRED", "REFRESH_METHOD", "FAST", "REFRESH_MODE", "COMMIT", "FAST_REFRESHABLE", "DML");
		Mview view = new OracleMviewReader(DialectHolder.oracle26aiDialect).createTable(new ExResultSet(null) {
			@Override public String getString(String key) { return values.get(key); }
			@Override public Object getObject(String key) { return values.get(key); }
		});
		Mview restored = SchemaUtils.readXml(new java.io.StringReader(view.asXml()));
		assertEquals("CREATE MATERIALIZED VIEW APP.MV BUILD DEFERRED REFRESH FAST ON COMMIT AS SELECT ID FROM APP.T", sql(restored, SqlType.CREATE));
	}
	@Test
	void createAndRefreshUseOracleSyntaxAcrossVersions() {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.oracle11gR2Dialect,
				DialectHolder.oracle19cDialect, DialectHolder.oracle21cDialect, DialectHolder.oracle23aiDialect, DialectHolder.oracle26aiDialect)) {
			var registry = dialect.createSqlFactoryRegistry();
			assertEquals("CREATE MATERIALIZED VIEW APP.MV AS SELECT ID FROM APP.T", registry.createSql(view(), SqlType.CREATE).get(0).getSqlText().replaceAll("\\s+", " "));
			assertEquals("BEGIN DBMS_MVIEW.REFRESH(list => 'APP.MV'); END;", registry.createSql(view(), SqlType.REFRESH).get(0).getSqlText());
		}
	}
	@Test
	void structuredColumnAliasesSurviveXml() throws Exception {
		Mview view = view();
		view.getColumns().add(new com.sqlapp.data.schemas.Column("Alias"));
		Mview restored = SchemaUtils.readXml(new java.io.StringReader(view.asXml()));
		String generated = sql(restored, SqlType.CREATE);
		assertTrue(generated.replaceAll("\\(\\s+", "(").contains("APP.MV (\"Alias\") AS"), generated);
	}
	@Test
	void neverRefreshAndInvalidOptionsFailExplicitly() {
		Mview view = view();
		view.getSpecifics().put("REFRESH_METHOD", "NEVER"); view.getSpecifics().put("REFRESH_MODE", "NEVER");
		assertTrue(sql(view, SqlType.CREATE).contains("NEVER REFRESH AS"));
		assertThrows(IllegalArgumentException.class, () -> sql(view, SqlType.REFRESH));
		view.getSpecifics().put("REFRESH_MODE", "DEMAND");
		assertThrows(IllegalArgumentException.class, () -> sql(view, SqlType.CREATE));
		for (var entry : Map.of("BUILD_MODE", "invalid", "REFRESH_MODE", "invalid", "REFRESH_METHOD", "invalid").entrySet()) {
			Mview invalid = view(); invalid.getSpecifics().put(entry.getKey(), entry.getValue());
			assertThrows(IllegalArgumentException.class, () -> sql(invalid, SqlType.CREATE));
		}
		assertThrows(IllegalArgumentException.class, () -> sql(new Mview("MV"), SqlType.CREATE));
	}
	@Test
	void completeDefinitionSupportsAdvancedFormsWithoutRewriting() {
		for (var entry : Map.of("BUILD_MODE", "PREBUILT", "REFRESH_MODE", "STATEMENT").entrySet()) {
			Mview view = view(); view.getSpecifics().put(entry.getKey(), entry.getValue());
			assertThrows(IllegalArgumentException.class, () -> sql(view, SqlType.CREATE));
			String ddl = "CREATE MATERIALIZED VIEW APP.MV ON PREBUILT TABLE AS SELECT ID FROM APP.T";
			view.setDefinition(ddl);
			assertEquals(ddl, sql(view, SqlType.CREATE));
		}
	}
	@Test
	void refreshQuotesIdentifiersAndStringLiterals() {
		Mview view = new Mview("Mixed'Name"); view.setSchemaName("MixedOwner");
		assertEquals("BEGIN DBMS_MVIEW.REFRESH(list => '\"MixedOwner\".\"Mixed''Name\"'); END;", sql(view, SqlType.REFRESH));
		view.setRemarks("must not become a refresh comment");
		assertFalse(sql(view, SqlType.REFRESH).contains("refresh comment"));
		assertThrows(IllegalArgumentException.class, () -> sql(new Mview(), SqlType.REFRESH));
	}
}
