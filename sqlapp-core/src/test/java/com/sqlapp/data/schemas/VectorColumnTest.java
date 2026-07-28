/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;

class VectorColumnTest {

	@Test
	void testXmlRoundTripAndEquality() throws Exception {
		final Catalog catalog = new Catalog("CAT");
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("DOCUMENTS");
		final Column vector = new Column("EMBEDDING")
				.setDataType(DataType.VECTOR)
				.setVectorElementDataType(DataType.REAL)
				.setVectorDimension(768);
		catalog.getSchemas().add(schema);
		schema.getTables().add(table);
		table.getColumns().add(vector);

		final Column cloned = vector.clone();
		assertEquals(vector, cloned);
		cloned.setVectorDimension(1536);
		assertNotEquals(vector, cloned);

		final StringWriter writer = new StringWriter();
		catalog.writeXml(writer);
		final Catalog restored = new Catalog();
		restored.loadXml(new StringReader(writer.toString()));
		final Column restoredVector = restored.getSchemas().get("PUBLIC")
				.getTables().get("DOCUMENTS").getColumns().get("EMBEDDING");
		assertEquals(DataType.VECTOR, restoredVector.getDataType());
		assertEquals(DataType.REAL, restoredVector.getVectorElementDataType());
		assertEquals(768, restoredVector.getVectorDimension());
		assertEquals(vector, restoredVector);
	}
}
