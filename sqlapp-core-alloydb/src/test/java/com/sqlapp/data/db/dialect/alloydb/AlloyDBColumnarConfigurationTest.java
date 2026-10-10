/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.alloydb;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;

class AlloyDBColumnarConfigurationTest {
	private Catalog catalog(String baseline) {
		var catalog = new Catalog("db");
		catalog.getSettings().add(new Setting(AlloyDBColumnarConfiguration.RELATIONS).setValue(baseline));
		var schema = new Schema("Mixed");
		catalog.getSchemas().add(schema);
		var table = new Table("Items");
		table.getColumns().add("Id", c -> c.setDataType(DataType.INT));
		table.getColumns().add("value", c -> c.setDataType(DataType.VARCHAR));
		schema.getTables().add(table);
		return catalog;
	}
	private Table table(Catalog catalog) { return catalog.getSchemas().get("Mixed").getTables().get("Items"); }

	@Test
	void readsSettingsWithoutJdbcAndPreservesUnmodeledRelations() throws Exception {
		var catalog = catalog("other.public.keep(id),db.Mixed.Items(Id),db.hidden.keep");
		AlloyDBColumnarConfiguration.read(catalog);
		assertEquals("Id", table(catalog).getSpecifics().get(AlloyDBColumnarConfiguration.COLUMNS));
		assertTrue(AlloyDBColumnarConfiguration.plan(catalog).sqlOperations().isEmpty());
		AlloyDBColumnarConfiguration.setColumns(table(catalog), "Id", "value");
		Catalog restored = SchemaUtils.readXml(new java.io.StringReader(catalog.asXml()));
		var plan = AlloyDBColumnarConfiguration.plan(restored);
		assertEquals("other.public.keep(id),db.Mixed.Items(Id,value),db.hidden.keep", plan.relations());
		assertEquals(2, plan.sqlOperations().size());
		assertTrue(plan.requiresAutoCommit());
		assertTrue(plan.sqlOperations().get(0).getSqlText().startsWith("ALTER SYSTEM SET google_columnar_engine.relations"));
		assertEquals("SELECT pg_catalog.pg_reload_conf()", plan.sqlOperations().get(1).getSqlText());
		assertEquals(catalog.getSettings().get(AlloyDBColumnarConfiguration.RELATIONS).getValue(), plan.originalRelations());
	}

	@Test
	void distinguishesAllColumnsExplicitRemovalAndUnspecifiedIntent() {
		var catalog = catalog("db.Mixed.Items");
		AlloyDBColumnarConfiguration.read(catalog);
		assertEquals("*", table(catalog).getSpecifics().get(AlloyDBColumnarConfiguration.COLUMNS));
		table(catalog).getSpecifics().remove(AlloyDBColumnarConfiguration.COLUMNS);
		assertTrue(AlloyDBColumnarConfiguration.plan(catalog).sqlOperations().isEmpty());
		AlloyDBColumnarConfiguration.clear(table(catalog));
		assertEquals("", AlloyDBColumnarConfiguration.plan(catalog).relations());
		catalog.getSettings().get(AlloyDBColumnarConfiguration.RELATIONS).setValue("");
		AlloyDBColumnarConfiguration.setAllColumns(table(catalog));
		assertEquals("db.Mixed.Items", AlloyDBColumnarConfiguration.plan(catalog).relations());
	}

	@Test
	void rejectsMissingBaselineAndInvalidModelColumnsOrNames() {
		var catalog = catalog("");
		assertThrows(IllegalArgumentException.class, () -> AlloyDBColumnarConfiguration.setColumns(table(catalog)));
		assertThrows(IllegalArgumentException.class, () -> AlloyDBColumnarConfiguration.setColumns(table(catalog), "missing"));
		assertThrows(IllegalArgumentException.class, () -> AlloyDBColumnarConfiguration.setColumns(table(catalog), "id"));
		assertThrows(IllegalArgumentException.class, () -> AlloyDBColumnarConfiguration.setColumns(table(catalog), "Id", "Id"));
		AlloyDBColumnarConfiguration.setAllColumns(table(catalog).setName("Odd.Table"));
		assertThrows(IllegalArgumentException.class, () -> AlloyDBColumnarConfiguration.plan(catalog));
		catalog.getSettings().clear();
		assertThrows(IllegalArgumentException.class, () -> AlloyDBColumnarConfiguration.plan(catalog));
	}

	@Test
	void rejectsMalformedOrAmbiguousGlobalSettings() {
		for (String value : new String[] { "db.Mixed.Items(", "db.Mixed.Items()", "db.Mixed.Items(Id,,value)",
				"db.Mixed.Items(Id,Id)", "db.Mixed.Items,db.Mixed.Items", "db.Mixed.Items,", "Mixed.Items",
				"db.\"Mixed Schema\".Items(Id)", "db.Mixed.Items(Id))", "db.Mixed.Items((Id))" }) {
			assertThrows(IllegalArgumentException.class, () -> AlloyDBColumnarConfiguration.read(catalog(value)), value);
		}
	}

	@Test
	void absentColumnarSettingDoesNotChangePostgresControlMetadata() {
		var catalog = catalog("");
		catalog.getSettings().clear();
		assertDoesNotThrow(() -> AlloyDBColumnarConfiguration.read(catalog));
		assertFalse(table(catalog).getSpecifics().containsKey(AlloyDBColumnarConfiguration.COLUMNS));
	}
	@Test
	void explicitClearSurvivesXmlAndOrdinaryCreateNeverAppliesInstanceSettings() throws Exception {
		var catalog = catalog("db.Mixed.Items(Id)");
		AlloyDBColumnarConfiguration.clear(table(catalog));
		Catalog restored = SchemaUtils.readXml(new java.io.StringReader(catalog.asXml()));
		assertEquals("", AlloyDBColumnarConfiguration.plan(restored).relations());
		AlloyDBColumnarConfiguration.setAllColumns(table(restored));
		com.sqlapp.data.db.sql.SqlFactory<Table> factory = new AlloyDB16().createSqlFactoryRegistry()
				.getSqlFactory(table(restored), com.sqlapp.data.db.sql.SqlType.CREATE);
		assertTrue(factory.createSql(table(restored)).stream()
				.noneMatch(sql -> sql.getSqlText().contains("ALTER SYSTEM")));
	}

}
