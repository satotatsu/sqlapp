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
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.VectorDistanceType;
import com.sqlapp.data.db.dialect.oracle.metadata.OracleFunctionReader;
import com.sqlapp.data.db.dialect.oracle.metadata.OracleSequenceReader;

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

	@Test
	void testScalableAndSessionSequences() {
		final Sequence scalable = new Sequence("ORDER_SEQ");
		scalable.setDialect(dialect);
		scalable.getSpecifics().put(OracleSequenceReader.SCALE, true);
		scalable.getSpecifics().put(OracleSequenceReader.EXTEND, true);
		final String scalableSql = sqlFactoryRegistry
				.createSql(scalable, SqlType.CREATE).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(scalableSql.contains("SCALE EXTEND"), scalableSql);

		final Sequence session = new Sequence("TEMP_ROW_SEQ");
		session.setDialect(dialect);
		session.getSpecifics().put(OracleSequenceReader.SESSION, true);
		final String sessionSql = sqlFactoryRegistry
				.createSql(session, SqlType.CREATE).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(sessionSql.contains(" SESSION"), sessionSql);
	}

	@Test
	void testSequenceVersionBoundary() {
		final Sequence scalable = new Sequence("ORDER_SEQ");
		scalable.getSpecifics().put(OracleSequenceReader.SCALE, true);

		final var oracle12 = DialectResolver.getInstance()
				.getDialect("Oracle", 12, 2, 0);
		scalable.setDialect(oracle12);
		final String oracle12Sql = oracle12.createSqlFactoryRegistry()
				.createSql(scalable, SqlType.CREATE).get(0).getSqlText();
		assertTrue(!oracle12Sql.contains("SCALE"), oracle12Sql);

		final var oracle18 = DialectResolver.getInstance()
				.getDialect("Oracle", 18, 0, 0);
		scalable.setDialect(oracle18);
		final String oracle18Sql = oracle18.createSqlFactoryRegistry()
				.createSql(scalable, SqlType.CREATE).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(oracle18Sql.contains("SCALE NOEXTEND"), oracle18Sql);
	}

	@Test
	void testScalarAndTableSqlMacros() {
		final Function scalar = createSqlMacro("NORMALIZE_CODE",
				FunctionType.Scalar, "RETURN 'UPPER(code)';");
		final String scalarSql = sqlFactoryRegistry
				.createSql(scalar, SqlType.CREATE).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(scalarSql.contains("SQL_MACRO(SCALAR)"), scalarSql);

		final Function table = createSqlMacro("ACTIVE_ORDERS",
				FunctionType.Table, "RETURN 'SELECT * FROM ORDERS WHERE ACTIVE = 1';");
		final String tableSql = sqlFactoryRegistry
				.createSql(table, SqlType.CREATE).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(tableSql.contains("SQL_MACRO(TABLE)"), tableSql);
	}

	@Test
	void testSqlMacroVersionBoundary() {
		final var oracle19 = DialectResolver.getInstance()
				.getDialect("Oracle", 19, 0, 0);
		final Function tableMacro = createSqlMacro("ACTIVE_ORDERS",
				FunctionType.Table,
				"RETURN 'SELECT * FROM ORDERS WHERE ACTIVE = 1';");
		tableMacro.setDialect(oracle19);
		final String oracle19Sql = oracle19.createSqlFactoryRegistry()
				.createSql(tableMacro, SqlType.CREATE).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(oracle19Sql.contains("SQL_MACRO"), oracle19Sql);
		assertTrue(!oracle19Sql.contains("SQL_MACRO(TABLE)"), oracle19Sql);

		final Function scalarMacro = createSqlMacro("NORMALIZE_CODE",
				FunctionType.Scalar, "RETURN 'UPPER(code)';");
		scalarMacro.setDialect(oracle19);
		assertThrows(IllegalArgumentException.class,
				() -> oracle19.createSqlFactoryRegistry()
						.createSql(scalarMacro, SqlType.CREATE));

		final var oracle21 = DialectResolver.getInstance()
				.getDialect("Oracle", 21, 0, 0);
		scalarMacro.setDialect(oracle21);
		final String oracle21Sql = oracle21.createSqlFactoryRegistry()
				.createSql(scalarMacro, SqlType.CREATE).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(oracle21Sql.contains("SQL_MACRO(SCALAR)"), oracle21Sql);
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

	private Function createSqlMacro(final String name,
			final FunctionType functionType, final String statement) {
		final Function function = new Function(name);
		function.setDialect(dialect);
		function.setFunctionType(functionType);
		function.getReturning().setDataType(DataType.VARCHAR);
		function.getReturning().setLength(4000);
		function.setStatement(statement);
		function.getSpecifics().put(OracleFunctionReader.SQL_MACRO, true);
		return function;
	}
}
