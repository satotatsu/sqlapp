/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.VectorDistanceType;

class Db2VectorIndexSqlTest extends AbstractDb2SqlFactoryTest {

	@Override
	protected int getMajorVersion() {
		return 12;
	}

	@Override
	protected int getMinorVersion() {
		return 1;
	}

	@Override
	protected int getRevision() {
		return 5;
	}

	@Test
	void testCreateVectorIndexWithTuningOptions() {
		final Table table = createVectorTable(DataType.REAL);
		final Index index = new Index("IDX_DOCUMENTS_EMBEDDING", table.getColumns().get("EMBEDDING"))
				.setIndexType(IndexType.Vector)
				.setVectorDistanceType(VectorDistanceType.Cosine)
				.setTableSpaceName("INDEX_TS");
		index.getSpecifics().put(Db2_1215CreateIndexFactory.COMPRESSED_VECTORS_TABLE_SPACE_NAME, "VECTOR_TS");
		index.getSpecifics().put(Db2_1215CreateIndexFactory.EXCLUDE_NULL_KEYS, "true");
		index.getSpecifics().put(Db2_1215CreateIndexFactory.BUILD_MEM_BUDGET, "16");
		index.getSpecifics().put(Db2_1215CreateIndexFactory.BUILD_LIST_SIZE, "100");
		index.getSpecifics().put(Db2_1215CreateIndexFactory.BUILD_PARALLELISM, "4");
		index.getSpecifics().put(Db2_1215CreateIndexFactory.MAX_NODE_DEGREE, "64");
		index.getSpecifics().put(Db2_1215CreateIndexFactory.PCT_COMP_VECT_SIZE, "10");
		index.getSpecifics().put(Db2_1215CreateIndexFactory.PCT_NODES_TO_CACHE, "25");
		table.getIndexes().add(index);

		final String sql = sqlFactoryRegistry.createSql(index, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains("CREATE VECTOR INDEX IDX_DOCUMENTS_EMBEDDING ON DOCUMENTS"), sql);
		assertTrue(sql.matches(".*\\(\\s*EMBEDDING\\s*\\).*WITH DISTANCE COSINE.*"), sql);
		assertTrue(sql.contains("IN INDEX_TS"), sql);
		assertTrue(sql.contains("COMPRESSED VECTORS IN VECTOR_TS"), sql);
		assertTrue(sql.contains("EXCLUDE NULL KEYS"), sql);
		assertTrue(sql.contains("BUILD_MEM_BUDGET 16"), sql);
		assertTrue(sql.contains("BUILD_LIST_SIZE 100"), sql);
		assertTrue(sql.contains("BUILD_PARALLELISM 4"), sql);
		assertTrue(sql.contains("MAX_NODE_DEGREE 64"), sql);
		assertTrue(sql.contains("PCT_COMP_VECT_SIZE 10"), sql);
		assertTrue(sql.contains("PCT_NODES_TO_CACHE 25"), sql);
	}

	@Test
	void testRejectCosineForInt8Vector() {
		final Table table = createVectorTable(DataType.TINYINT);
		final Index index = new Index("IDX_DOCUMENTS_EMBEDDING", table.getColumns().get("EMBEDDING"))
				.setIndexType(IndexType.Vector)
				.setVectorDistanceType(VectorDistanceType.Cosine);
		table.getIndexes().add(index);

		assertThrows(IllegalArgumentException.class,
				() -> sqlFactoryRegistry.createSql(index, SqlType.CREATE));
	}

	@Test
	void testRejectOutOfRangeOption() {
		final Table table = createVectorTable(DataType.REAL);
		final Index index = new Index("IDX_DOCUMENTS_EMBEDDING", table.getColumns().get("EMBEDDING"))
				.setIndexType(IndexType.Vector)
				.setVectorDistanceType(VectorDistanceType.Euclidean);
		index.getSpecifics().put(Db2_1215CreateIndexFactory.MAX_NODE_DEGREE, "129");
		table.getIndexes().add(index);

		assertThrows(IllegalArgumentException.class,
				() -> sqlFactoryRegistry.createSql(index, SqlType.CREATE));
	}

	private Table createVectorTable(final DataType elementType) {
		final Table table = new Table("DOCUMENTS");
		table.setDialect(dialect);
		final Column column = new Column("EMBEDDING")
				.setDataType(DataType.VECTOR)
				.setVectorElementDataType(elementType)
				.setVectorDimension(768);
		table.getColumns().add(column);
		return table;
	}
}
