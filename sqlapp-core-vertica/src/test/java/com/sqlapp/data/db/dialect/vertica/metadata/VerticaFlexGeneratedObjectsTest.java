package com.sqlapp.data.db.dialect.vertica.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.vertica.metadata.VerticaFlexGeneratedObjects;
import com.sqlapp.data.db.dialect.vertica.metadata.VerticaSchemaRestorePlan;
import com.sqlapp.data.schemas.Schema;

class VerticaFlexGeneratedObjectsTest {
	static void addRaw(Schema schema, String name) {
		var table = new com.sqlapp.data.schemas.Table(name);
		table.setTableType(com.sqlapp.data.schemas.Table.TableType.Flex);
		table.getColumns().add("__raw__", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.LONGVARBINARY)
				.setLength(130000L).setNotNull(true));
		schema.getTables().add(table);
	}

	@Test
	void explicitlyEmptySchedulingSnapshotSurvivesXmlWhileMissingKeyStillFails() throws Exception {
		Schema source = schema();
		Schema xml = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(source.asXml()));
		assertTrue(xml.getSpecifics().containsKey("VERTICA_SCHEDULING_STATES"));
		assertEquals(2, VerticaSchemaRestorePlan.create(xml).generatedCheckpoint());
		xml.getSpecifics().remove("VERTICA_SCHEDULING_STATES");
		assertThrows(IllegalArgumentException.class, () -> VerticaSchemaRestorePlan.create(xml));
	}

	private Schema schema() {
		Schema schema = new Schema("app");
		addRaw(schema, "f");
		schema.getSpecifics().put("VERTICA_SCHEDULING_STATES", "");
		schema.setDefinition("CREATE SCHEMA app; CREATE FLEX TABLE app.f(id INT); "
				+ "CREATE PROJECTION app.f_super AS SELECT id FROM app.f ORDER BY id UNSEGMENTED ALL NODES; "
				+ "CREATE VIEW app.f_view AS SELECT id FROM app.f; "
				+ "CREATE PROJECTION app.custom AS SELECT id FROM app.f UNSEGMENTED ALL NODES;");
		return schema;
	}

	private String generated() {
		return "CREATE SCHEMA app; CREATE FLEX TABLE app.f(id INT); "
				+ "CREATE PROJECTION app.f_super AS SELECT id FROM app.f ORDER BY id UNSEGMENTED ALL NODES; "
				+ "CREATE VIEW app.f_view AS SELECT id FROM app.f;";
	}

	@Test
	void matchesOnlyActualGeneratedDdlWithoutNameSuffixInferenceOrDroppingObjects() throws Exception {
		Schema schema = schema();
		var plan = VerticaSchemaRestorePlan.create(schema);
		assertEquals(2, plan.generatedCheckpoint());
		var matched = VerticaFlexGeneratedObjects.matchingStatements(schema, plan, generated());
		assertEquals(2, matched.size());
		assertTrue(matched.stream().anyMatch(s -> s.startsWith("CREATE VIEW")));
		assertFalse(matched.stream().anyMatch(s -> s.contains("app.custom")));
		assertTrue(plan.statements().stream().noneMatch(s -> s.startsWith("DROP") || s.contains("MAKE_AHM")));
	}

	@Test
	void changedMissingExtraOrDuplicateDefinitionsFailAsASet() {
		Schema schema = schema();
		var plan = VerticaSchemaRestorePlan.create(schema);
		for (String ddl : List.of(generated().replace("ORDER BY id", "ORDER BY id DESC"),
				generated().replace("app.f_super", "app.unexpected"),
				generated() + "CREATE VIEW app.f_view AS SELECT id FROM app.f;"))
			assertThrows(SQLException.class, () -> VerticaFlexGeneratedObjects.matchingStatements(schema, plan, ddl));
	}

	@Test
	void contiguousFlexBatchSharesOneCheckpointAndOrdinaryFactoryGuardRemains() {
		Schema schema = schema();
		schema.setDefinition("CREATE SCHEMA app; CREATE FLEX TABLE app.f(); CREATE FLEX TABLE app.g();"
				+ "CREATE PROJECTION app.p AS SELECT __identity__ FROM app.f;");
		addRaw(schema, "g");
		assertEquals(3, VerticaSchemaRestorePlan.create(schema).generatedCheckpoint());
		assertThrows(IllegalArgumentException.class,
				() -> com.sqlapp.data.db.dialect.vertica.DialectHolder.defaultDialect12_0_4.createSqlFactoryRegistry()
						.createSql(schema, com.sqlapp.data.db.sql.SqlType.CREATE));
	}
}
