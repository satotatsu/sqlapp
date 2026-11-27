package com.sqlapp.data.db.dialect.vertica.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.vertica.metadata.VerticaDdlExporter;
import com.sqlapp.data.db.dialect.vertica.metadata.VerticaSchemaRestorePlan;
import com.sqlapp.data.db.dialect.vertica.metadata.VerticaSchemaRestorer;
import com.sqlapp.data.schemas.*;

class VerticaFlexRawColumnsTest {
	@Test
	void quotedMixedCaseTableMetadataResolvesAfterXmlAndMissingRawIsRejectedOffline() throws Exception {
		var jdbc = jdbc();
		jdbc.generatedExport = "CREATE SCHEMA app; CREATE FLEX TABLE app.\"F.Dot\"(id INT);";
		jdbc.rawRows = Collections.singletonList(new Object[] { "app", "F.Dot", 130000L, false });
		Schema source = new Schema("app");
		new VerticaDdlExporter().exportSchemaDefinition(jdbc.connection(), source);
		Schema xml = SchemaUtils.readXml(new java.io.StringReader(source.asXml()));
		assertEquals(2, VerticaSchemaRestorePlan.create(xml).generatedCheckpoint());
		xml.getTables().clear();
		assertThrows(IllegalArgumentException.class, () -> VerticaSchemaRestorePlan.create(xml));
	}

	private VerticaSchemaRestorerTest.Jdbc jdbc() {
		var jdbc = new VerticaSchemaRestorerTest.Jdbc();
		jdbc.states.clear();
		jdbc.generatedExport = "CREATE SCHEMA app; CREATE FLEX TABLE app.f(id INT);";
		jdbc.rawRows = Collections.singletonList(new Object[] { "app", "f", 130000L, false });
		return jdbc;
	}

	@Test
	void exportKeepsCompleteTableDefinitionAndImplicitRawMetadataAcrossXml() throws Exception {
		var jdbc = jdbc();
		var schema = new Schema("app");
		var existing = new Table("f");
		existing.getColumns().add("id", c -> c.setDataType(DataType.BIGINT));
		schema.getTables().add(existing);
		new VerticaDdlExporter().exportSchemaDefinition(jdbc.connection(), schema);
		assertEquals(1, jdbc.exports);
		assertEquals(1, jdbc.bulkReads);
		assertEquals(1, jdbc.rawReads);
		Schema xml = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		var table = xml.getTables().get("f");
		assertTrue(String.join("\n", table.getDefinition()).contains("CREATE FLEX TABLE app.f(id INT)"));
		assertNotNull(table.getColumns().get("id"));
		assertEquals(130000L, table.getColumns().get("__raw__").getLength());
		assertTrue(table.getColumns().get("__raw__").isNotNull());
		assertEquals(DataType.LONGVARBINARY, table.getColumns().get("__raw__").getDataType());
	}

	@Test
	void incompleteInvalidAndDuplicateMetadataLeaveExistingModelUnchanged() {
		for (List<Object[]> rows : List.of(List.<Object[]>of(),
				Collections.singletonList(new Object[] { "other", "f", 130000L, false }),
				Collections.singletonList(new Object[] { "app", "f", null, false }),
				Collections.singletonList(new Object[] { "app", "f", 0L, false }),
				Collections.singletonList(new Object[] { "app", "f", 130000L, null }), List.<Object[]>of(
						new Object[] { "app", "f", 130000L, false }, new Object[] { "app", "F", 130000L, false }))) {
			var jdbc = jdbc();
			jdbc.rawRows = rows;
			var schema = new Schema("app");
			schema.setDefinition("old definition");
			schema.getSpecifics().put("VERTICA_SCHEDULING_STATES", "old states");
			assertThrows(SQLException.class,
					() -> new VerticaDdlExporter().exportSchemaDefinition(jdbc.connection(), schema));
			assertEquals(List.of("old definition"), schema.getDefinition());
			assertEquals("old states", schema.getSpecifics().get("VERTICA_SCHEDULING_STATES"));
			assertTrue(schema.getTables().isEmpty());
		}
	}

	@Test
	void targetWidthMismatchIsReportedBeforeRemainingDdl() {
		var jdbc = jdbc();
		jdbc.rawRows = Collections.singletonList(new Object[] { "app", "f", 260000L, false });
		jdbc.generatedExport += "CREATE PROJECTION app.p AS SELECT id FROM app.f UNSEGMENTED ALL NODES;";
		var schema = new Schema("app");
		schema.setDefinition(jdbc.generatedExport);
		schema.getSpecifics().put("VERTICA_SCHEDULING_STATES", "");
		VerticaFlexGeneratedObjectsTest.addRaw(schema, "f");
		var failure = assertThrows(VerticaSchemaRestorer.RestorationException.class,
				() -> new VerticaSchemaRestorer().restore(jdbc.connection(), schema));
		assertEquals(VerticaSchemaRestorer.Phase.GENERATED_OBJECTS, failure.getPhase());
		assertEquals(2, failure.getCompletedStatements().size());
		assertEquals(2, jdbc.attempted.size());
	}
}
