/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.VectorDistanceType;

class OracleModernSchemaSqlTest extends AbstractOracleSqlFactoryTest {

	@Override
	protected int getMajorVersion() {
		return 23;
	}

	@Test
	void testNativeJsonBooleanAndVectorTypes() {
		final Table table = new Table("DOCUMENTS");
		table.setDialect(dialect);
		table.getColumns().add("PAYLOAD", column -> column.setDataType(DataType.JSON));
		table.getColumns().add("ENABLED", column -> column.setDataType(DataType.BOOLEAN));
		table.getColumns().add(new Column("FLOAT_EMBEDDING")
				.setDataType(DataType.VECTOR)
				.setVectorDimension(768)
				.setVectorElementDataType(DataType.REAL));
		table.getColumns().add(new Column("DOUBLE_EMBEDDING")
				.setDataType(DataType.VECTOR)
				.setVectorDimension(256)
				.setVectorElementDataType(DataType.DOUBLE));
		table.getColumns().add(new Column("FLEXIBLE_EMBEDDING")
				.setDataType(DataType.VECTOR)
				.setVectorElementDataType(DataType.TINYINT));
		table.getColumns().add(new Column("BINARY_EMBEDDING")
				.setDataType(DataType.VECTOR)
				.setVectorDimension(1024)
				.setVectorElementDataType(DataType.BINARY));

		final String sql = sqlFactoryRegistry.createSql(table, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.matches(".*PAYLOAD\\s+JSON.*"), sql);
		assertTrue(sql.matches(".*ENABLED\\s+BOOLEAN.*"), sql);
		assertTrue(sql.matches(".*FLOAT_EMBEDDING\\s+VECTOR\\(\\s*768\\s*,\\s*FLOAT32\\s*\\).*"), sql);
		assertTrue(sql.matches(".*DOUBLE_EMBEDDING\\s+VECTOR\\(\\s*256\\s*,\\s*FLOAT64\\s*\\).*"), sql);
		assertTrue(sql.matches(".*FLEXIBLE_EMBEDDING\\s+VECTOR\\(\\s*\\*\\s*,\\s*INT8\\s*\\).*"), sql);
		assertTrue(sql.matches(".*BINARY_EMBEDDING\\s+VECTOR\\(\\s*1024\\s*,\\s*BINARY\\s*\\).*"), sql);
	}

	@Test
	void testCreateHnswVectorIndex() {
		final Table table = createVectorTable();
		final Index index = new Index("IDX_DOCUMENTS_EMBEDDING", table.getColumns().get("EMBEDDING"))
				.setIndexType(IndexType.Vector)
				.setVectorDistanceType(VectorDistanceType.Cosine);
		index.getSpecifics().put(Oracle23aiCreateIndexFactory.ORGANIZATION, "HNSW");
		index.getSpecifics().put(Oracle23aiCreateIndexFactory.TARGET_ACCURACY, "95");
		index.getSpecifics().put(Oracle23aiCreateIndexFactory.NEIGHBORS, "40");
		index.getSpecifics().put(Oracle23aiCreateIndexFactory.EFCONSTRUCTION, "500");
		table.getIndexes().add(index);

		final String sql = sqlFactoryRegistry.createSql(index, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains("CREATE VECTOR INDEX IDX_DOCUMENTS_EMBEDDING ON DOCUMENTS"), sql);
		assertTrue(sql.contains("ORGANIZATION INMEMORY NEIGHBOR GRAPH"), sql);
		assertTrue(sql.contains("DISTANCE COSINE"), sql);
		assertTrue(sql.contains("WITH TARGET ACCURACY 95"), sql);
		assertTrue(sql.matches(".*PARAMETERS\\s*\\(\\s*type HNSW\\s*,\\s*neighbors 40\\s*,\\s*efconstruction 500\\s*\\).*"),
				sql);
	}

	@Test
	void testCreateIvfVectorIndex() {
		final Table table = createVectorTable();
		final Index index = new Index("IDX_DOCUMENTS_EMBEDDING", table.getColumns().get("EMBEDDING"))
				.setIndexType(IndexType.Vector)
				.setVectorDistanceType(VectorDistanceType.EuclideanSquared);
		index.getSpecifics().put(Oracle23aiCreateIndexFactory.ORGANIZATION, "IVF");
		index.getSpecifics().put(Oracle23aiCreateIndexFactory.NEIGHBOR_PARTITIONS, "100");
		index.getSpecifics().put(Oracle23aiCreateIndexFactory.PARALLEL, "8");
		table.getIndexes().add(index);

		final String sql = sqlFactoryRegistry.createSql(index, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains("ORGANIZATION NEIGHBOR PARTITIONS"), sql);
		assertTrue(sql.contains("DISTANCE EUCLIDEAN SQUARED"), sql);
		assertTrue(sql.matches(".*PARAMETERS\\s*\\(\\s*type IVF\\s*,\\s*neighbor partitions 100\\s*\\).*"), sql);
		assertTrue(sql.contains("PARALLEL 8"), sql);
	}

	@Test
	void testRejectVectorBeforeOracle23ai() {
		final var oracle21 = DialectResolver.getInstance().getDialect("Oracle", 21, 0, 0);
		final Table table = new Table("DOCUMENTS");
		table.setDialect(oracle21);
		table.getColumns().add(new Column("EMBEDDING")
				.setDataType(DataType.VECTOR)
				.setVectorDimension(3)
				.setVectorElementDataType(DataType.REAL));

		assertThrows(IllegalArgumentException.class,
				() -> oracle21.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
	}

	private Table createVectorTable() {
		final Table table = new Table("DOCUMENTS");
		table.setDialect(dialect);
		table.getColumns().add(new Column("EMBEDDING")
				.setDataType(DataType.VECTOR)
				.setVectorDimension(768)
				.setVectorElementDataType(DataType.REAL));
		return table;
	}
}
