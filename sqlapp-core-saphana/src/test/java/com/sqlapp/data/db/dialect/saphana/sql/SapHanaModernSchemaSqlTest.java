/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 */
package com.sqlapp.data.db.dialect.saphana.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.VectorDistanceType;

class SapHanaModernSchemaSqlTest extends AbstractSapHanaSqlFactoryTest {

	@Test
	void testBooleanJsonAndRealVector() {
		final Table table = new Table("DOCUMENTS");
		table.setDialect(dialect);
		table.getColumns().add(
				new Column("ENABLED").setDataType(DataType.BOOLEAN));
		table.getColumns().add(
				new Column("PAYLOAD").setDataType(DataType.JSON));
		table.getColumns().add(new Column("EMBEDDING")
				.setDataType(DataType.VECTOR)
				.setVectorElementDataType(DataType.REAL)
				.setVectorDimension(768));

		final String sql = sqlFactoryRegistry.createSql(table, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains("ENABLED BOOLEAN"), sql);
		assertTrue(sql.contains("PAYLOAD JSON"), sql);
		assertTrue(sql.matches(
				".*EMBEDDING REAL_VECTOR\\(\\s*768\\s*\\).*"), sql);

		final Column metadataColumn = new Column();
		metadataColumn.setDialect(dialect);
		metadataColumn.setDataTypeName("REAL_VECTOR");
		assertEquals(DataType.VECTOR, metadataColumn.getDataType());
	}

	@Test
	void testCreateHnswVectorIndex() {
		final Table table = new Table("DOCUMENTS");
		table.setDialect(dialect);
		final Column embedding = new Column("EMBEDDING")
				.setDataType(DataType.VECTOR)
				.setVectorElementDataType(DataType.REAL)
				.setVectorDimension(768);
		table.getColumns().add(embedding);
		final Index index = new Index("IDX_DOCUMENTS_EMBEDDING", embedding)
				.setIndexType(IndexType.Vector)
				.setVectorDistanceType(VectorDistanceType.Cosine);
		index.getSpecifics().put(
				SapHanaCreateIndexFactory.BUILD_CONFIGURATION,
				"{\"M\":64,\"efConstruction\":128}");
		index.getSpecifics().put(
				SapHanaCreateIndexFactory.SEARCH_CONFIGURATION,
				"{\"efSearch\":64}");
		index.getSpecifics().put(SapHanaCreateIndexFactory.ONLINE, true);
		table.getIndexes().add(index);

		final String sql = sqlFactoryRegistry.createSql(index, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains("CREATE HNSW VECTOR INDEX "
				+ "IDX_DOCUMENTS_EMBEDDING ON DOCUMENTS"), sql);
		assertTrue(sql.contains("SIMILARITY FUNCTION COSINE_SIMILARITY"),
				sql);
		assertTrue(sql.contains("BUILD CONFIGURATION "
				+ "'{\"M\":64,\"efConstruction\":128}'"), sql);
		assertTrue(sql.contains("SEARCH CONFIGURATION "
				+ "'{\"efSearch\":64}' ONLINE"), sql);
	}
}
