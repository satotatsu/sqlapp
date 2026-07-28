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

class VectorIndexTest {

	@Test
	void testXmlRoundTripAndEquality() throws Exception {
		final Catalog catalog = new Catalog("CAT");
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("DOCUMENTS");
		final Column vector = new Column("EMBEDDING")
				.setDataType(DataType.VECTOR)
				.setVectorElementDataType(DataType.REAL)
				.setVectorDimension(768);
		final Index index = new Index("IDX_DOCUMENTS_EMBEDDING", vector)
				.setIndexType(IndexType.Vector)
				.setVectorDistanceType(VectorDistanceType.Cosine);
		catalog.getSchemas().add(schema);
		schema.getTables().add(table);
		table.getColumns().add(vector);
		table.getIndexes().add(index);

		final Index cloned = index.clone();
		assertEquals(index, cloned);
		cloned.setVectorDistanceType(VectorDistanceType.Euclidean);
		assertNotEquals(index, cloned);

		final StringWriter writer = new StringWriter();
		catalog.writeXml(writer);
		final Catalog restored = new Catalog();
		restored.loadXml(new StringReader(writer.toString()));
		final Index restoredIndex = restored.getSchemas().get("PUBLIC")
				.getTables().get("DOCUMENTS").getIndexes().get("IDX_DOCUMENTS_EMBEDDING");
		assertEquals(IndexType.Vector, restoredIndex.getIndexType());
		assertEquals(VectorDistanceType.Cosine, restoredIndex.getVectorDistanceType());
		assertEquals(index, restoredIndex);
	}
}
