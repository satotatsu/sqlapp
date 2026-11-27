package com.sqlapp.data.db.dialect.vertica.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.db.dialect.vertica.DialectHolder;
import com.sqlapp.data.db.dialect.vertica.metadata.VerticaDdlExporter;
import com.sqlapp.data.db.sql.SqlType;

class VerticaProjectionDefinitionMappingTest {
	private final VerticaDdlExporter exporter = new VerticaDdlExporter();

	private Projection projection(String schema, String name) {
		Projection p = new Projection(name);
		p.setSchemaName(schema);
		return p;
	}

	private String ddl(String qualified) {
		return "CREATE PROJECTION " + qualified + " AS SELECT id FROM "
				+ qualified.substring(0, qualified.lastIndexOf('.')) + ".t UNSEGMENTED ALL NODES;";
	}

	@Test
	void separatesDependenciesAndBuddyDefinitionsAndPreservesExecutableXml() throws Exception {
		Projection first = projection("physical", "p_b0");
		Projection second = projection("physical", "p_b1");
		first.getSpecifics().put("PROJECTION_BASENAME", "p");
		second.getSpecifics().put("PROJECTION_BASENAME", "p");
		String script = "CREATE TABLE physical.t(id INT);\n"
				+ "CREATE FUNCTION source.f() RETURN INT AS BEGIN RETURN 1; END;\n"
				+ "CREATE PROJECTION physical.p_b0 /*+basename(p)*/ (id ENCODING RLE) AS SELECT id FROM physical.t SEGMENTED BY hash(id) ALL NODES OFFSET 0;\n"
				+ "CREATE PROJECTION physical.p_b1 /*+basename(p)*/ AS SELECT id FROM physical.t SEGMENTED BY hash(id) ALL NODES OFFSET 1;\n"
				+ ddl("physical.unrequested") + " SELECT MARK_DESIGN_KSAFE(1);";
		exporter.applyProjectionDefinitions(script, List.of(first, second));
		assertTrue(String.join("\n", first.getDefinition()).contains("OFFSET 0"));
		assertFalse(String.join("\n", first.getDefinition()).contains("OFFSET 1"));
		for (Projection p : List.of(first, second)) {
			Projection restored = SchemaUtils.readXml(new java.io.StringReader(p.asXml()));
			String sql = DialectHolder.defaultDialect12_0_4.createSqlFactoryRegistry()
					.createSql(restored, SqlType.CREATE).get(0).getSqlText();
			assertFalse(sql.contains("CREATE TABLE"), sql);
			assertFalse(sql.contains("MARK_DESIGN_KSAFE"), sql);
			assertFalse(sql.contains("unrequested"), sql);
			assertTrue(sql.contains("/*+basename(p)*/"), sql);
			assertEquals(1, DialectHolder.defaultDialect.createSqlSplitter().parse(sql).size());
		}
	}

	@Test
	void quotedNamesCommentsCaseAndLiteralSemicolonsAreMatched() {
		Projection p = projection("physical space", "p\".,name");
		String script = "CREATE /*header*/ PROJECTION IF NOT EXISTS \"PHYSICAL SPACE\".\"P\"\".,NAME\" "
				+ "(\"id)\" ENCODING RLE) AS SELECT 'value; -- comment' FROM \"PHYSICAL SPACE\".t UNSEGMENTED ALL NODES -- trailing comment\n;";
		exporter.applyProjectionDefinitions(script, List.of(p, p));
		String ddl = String.join("\n", p.getDefinition());
		assertTrue(ddl.contains("value; -- comment"));
		assertTrue(ddl.endsWith("\n;"), ddl);
		assertEquals(1, DialectHolder.defaultDialect.createSqlSplitter().parse(ddl).size());
	}

	@Test
	void missingDuplicateMalformedAndContextDependentScriptsLeaveAllTargetsUnchanged() throws Exception {
		Projection first = projection("app", "p1");
		Projection second = projection("app", "p2");
		first.setDefinition("original first");
		second.setDefinition("original second");
		List<String> before = List.of(first.asXml(), second.asXml());
		for (String invalid : List.of(ddl("app.p1"), ddl("app.p1") + ddl("APP.P1") + ddl("app.p2"),
				"CREATE PROJECTION app.p1();" + ddl("app.p2"),
				"CREATE OR REPLACE PROJECTION app.p1 AS SELECT id FROM app.t;" + ddl("app.p2"),
				"CREATE PROJECTION p1 AS SELECT 1;" + ddl("app.p2"),
				"SET SEARCH_PATH TO app;" + ddl("app.p1") + ddl("app.p2"),
				ddl("app.p1") + ddl("app.p2") + "ALTER PROJECTION app.p1 DISABLE;")) {
			assertThrows(IllegalArgumentException.class,
					() -> exporter.applyProjectionDefinitions(invalid, List.of(first, second)), invalid);
			assertEquals(before, List.of(first.asXml(), second.asXml()));
		}
	}

	@Test
	void catalogAndSchemaIdentityAreNotGuessed() {
		Projection first = projection("app", "p");
		Projection second = projection("app", "p");
		first.setCatalogName("db1");
		second.setCatalogName("db2");
		assertThrows(IllegalArgumentException.class,
				() -> exporter.applyProjectionDefinitions(ddl("app.p"), List.of(first, second)));
		exporter.applyProjectionDefinitions(ddl("db1.app.p") + ddl("db2.app.p"), List.of(first, second));
		assertTrue(String.join("\n", first.getDefinition()).contains("db1.app.p"));
		assertTrue(String.join("\n", second.getDefinition()).contains("db2.app.p"));
		Projection duplicate = projection("APP", "P");
		duplicate.setCatalogName("DB1");
		assertThrows(IllegalArgumentException.class,
				() -> exporter.applyProjectionDefinitions(ddl("db1.app.p"), List.of(first, duplicate)));
		Projection wrongSchema = projection("other", "p");
		assertThrows(IllegalArgumentException.class,
				() -> exporter.applyProjectionDefinitions(ddl("app.p"), List.of(wrongSchema)));
	}

	@Test
	void basenameHintsCannotStandInForMissingBuddyDefinitions() {
		Projection buddy = projection("app", "p_b1");
		buddy.getSpecifics().put("PROJECTION_BASENAME", "p");
		assertThrows(IllegalArgumentException.class, () -> exporter.applyProjectionDefinitions(
				"CREATE PROJECTION app.p /*+basename(p)*/ AS SELECT id FROM t;", List.of(buddy)));
		assertTrue(buddy.getDefinition() == null || buddy.getDefinition().isEmpty());
	}
}
