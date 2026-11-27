/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CrossSchemaForeignKeyXmlTest {
	@Test
	void referencedSchemaAndColumnsSurviveWithoutReferencedSchemaInModel() throws Exception {
		var schema = new Schema("LOCAL");
		var local = new Table("T");
		local.getColumns().add(new Column("ID"));
		local.getColumns().add(new Column("REMOTE_ID"));
		schema.getTables().add(local);
		var remote = new Table("T").setSchemaName("REMOTE");
		remote.getColumns().add(new Column("ID"));
		var fk = new ForeignKeyConstraint("FK", local.getColumns().get("REMOTE_ID"), remote.getColumns().get("ID"));
		local.getConstraints().add(fk);
		assertEquals("REMOTE", fk.getRelatedTableSchemaName());
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		var copy = restored.getTables().get("T").getConstraints().getForeignKeyConstraints().get(0);
		assertEquals("REMOTE", copy.getRelatedTableSchemaName());
		assertEquals("T", copy.getRelatedTableName());
		assertEquals("REMOTE", copy.getRelatedColumns().get(0).getColumn().getSchemaName());
		assertNotSame(restored.getTables().get("T").getColumns().get("ID"), copy.getRelatedColumns().get(0).getColumn());
		assertEquals("REMOTE", restored.clone().getTables().get("T").getConstraints().getForeignKeyConstraints().get(0).getRelatedTableSchemaName());
	}

	@Test
	void dummyTableConversionRetainsSchema() {
		var dummy = new DummyTable("T").setSchemaName("REMOTE");
		assertEquals("REMOTE", dummy.toTable().getSchemaName());
	}
}
