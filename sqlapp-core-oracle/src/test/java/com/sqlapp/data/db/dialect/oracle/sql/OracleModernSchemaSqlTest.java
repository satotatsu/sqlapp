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
import com.sqlapp.data.schemas.Deferrability;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.VectorDistanceType;
import com.sqlapp.data.schemas.View;
import com.sqlapp.data.db.dialect.oracle.metadata.OracleFunctionReader;
import com.sqlapp.data.db.dialect.oracle.metadata.OracleSequenceReader;

class OracleModernSchemaSqlTest extends AbstractOracleSqlFactoryTest {

	@Override
	protected int getMajorVersion() {
		return 23;
	}

	@Test
	void testCreateAndDropJsonRelationalDualityView() {
		final View view = new View("CUSTOMER_DV");
		view.setDialect(dialect);
		view.setStatement("""
				CUSTOMERS @insert @update @delete {
				  _id: ID,
				  name: NAME,
				  orders: ORDERS @insert @update {
				    orderId: ID,
				    total: TOTAL
				  }
				}""");
		OracleJsonDualityViewUtils.setJsonRelationalDualityView(view, true);

		final String createSql = sqlFactoryRegistry
				.createSql(view, SqlType.CREATE).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(createSql.contains("CREATE JSON RELATIONAL DUALITY VIEW "
				+ "IF NOT EXISTS CUSTOMER_DV AS"), createSql);
		assertTrue(createSql.contains(
				"CUSTOMERS @insert @update @delete"), createSql);
		assertTrue(createSql.contains("orders: ORDERS @insert @update"),
				createSql);

		final String dropSql = sqlFactoryRegistry
				.createSql(view, SqlType.DROP).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(dropSql.contains("DROP VIEW IF EXISTS CUSTOMER_DV"),
				dropSql);
	}

	@Test
	void testRejectJsonRelationalDualityViewWithoutStatement() {
		final View view = new View("EMPTY_DV");
		view.setDialect(dialect);
		OracleJsonDualityViewUtils.setJsonRelationalDualityView(view, true);

		assertThrows(IllegalArgumentException.class,
				() -> sqlFactoryRegistry.createSql(view, SqlType.CREATE));
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
		OracleAnnotationUtils.setAnnotation(index, "Purpose",
				"Semantic search");
		table.getIndexes().add(index);

		final String sql = sqlFactoryRegistry.createSql(index, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains("CREATE VECTOR INDEX IDX_DOCUMENTS_EMBEDDING ON DOCUMENTS"), sql);
		assertTrue(sql.contains("ORGANIZATION INMEMORY NEIGHBOR GRAPH"), sql);
		assertTrue(sql.contains("DISTANCE COSINE"), sql);
		assertTrue(sql.contains("WITH TARGET ACCURACY 95"), sql);
		assertTrue(sql.matches(".*PARAMETERS\\s*\\(\\s*type HNSW\\s*,\\s*neighbors 40\\s*,\\s*efconstruction 500\\s*\\).*"),
				sql);
		assertTrue(sql.contains(
				"ANNOTATIONS ( \"Purpose\" 'Semantic search')"), sql);
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
	void testCreateHybridVectorIndex() {
		final Table table = new Table("DOCUMENTS");
		table.setDialect(dialect);
		table.getColumns().add(new Column("CONTENT").setDataType(DataType.CLOB));
		final Index index = new Index("IDX_DOCUMENTS_HYBRID",
				table.getColumns().get("CONTENT")).setIndexType(IndexType.Vector);
		index.getSpecifics().put(Oracle23aiCreateIndexFactory.HYBRID, true);
		index.getSpecifics().put(Oracle23aiCreateIndexFactory.DATASTORE, "DOC_DATASTORE");
		index.getSpecifics().put(Oracle23aiCreateIndexFactory.FILTER, "CTXSYS.AUTO_FILTER");
		index.getSpecifics().put(Oracle23aiCreateIndexFactory.LEXER, "DOC_LEXER");
		index.getSpecifics().put(Oracle23aiCreateIndexFactory.MODEL, "DOC_MODEL");
		index.getSpecifics().put(Oracle23aiCreateIndexFactory.VECTOR_IDXTYPE, "IVF");
		table.getIndexes().add(index);

		final String sql = sqlFactoryRegistry.createSql(index, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains(
				"CREATE HYBRID VECTOR INDEX IDX_DOCUMENTS_HYBRID ON DOCUMENTS ( CONTENT )"),
				sql);
		assertTrue(sql.contains("PARAMETERS ('DATASTORE DOC_DATASTORE "
				+ "FILTER CTXSYS.AUTO_FILTER LEXER DOC_LEXER "
				+ "MODEL DOC_MODEL VECTOR_IDXTYPE IVF')"), sql);
	}

	@Test
	void testCreateAndDropDataUseCaseDomain() {
		final Domain domain = new Domain("EMAIL_ADDRESS");
		domain.setDialect(dialect);
		domain.setDataType(DataType.VARCHAR);
		domain.setLength(320L);
		domain.setDefaultValue("'unknown@example.com'");
		domain.setNotNull(true);
		domain.setCheck("REGEXP_LIKE(VALUE, '^[^@]+@[^@]+$')");
		domain.setDeferrability(Deferrability.InitiallyDeferred);
		domain.getSpecifics().put(Oracle23aiCreateDomainFactory.STRICT, true);
		domain.getSpecifics().put(
				Oracle23aiCreateDomainFactory.DEFAULT_ON_NULL, true);
		domain.getSpecifics().put(
				Oracle23aiCreateDomainFactory.CONSTRAINT_NAME,
				"CK_EMAIL_ADDRESS");
		domain.getSpecifics().put(Oracle23aiCreateDomainFactory.DISPLAY,
				"LOWER(VALUE)");
		domain.getSpecifics().put(Oracle23aiCreateDomainFactory.ORDER,
				"LOWER(VALUE)");
		OracleAnnotationUtils.setAnnotation(domain, "Display",
				"Email address");

		final String createSql = sqlFactoryRegistry
				.createSql(domain, SqlType.CREATE).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(createSql.contains(
				"CREATE DOMAIN IF NOT EXISTS EMAIL_ADDRESS AS VARCHAR2(320) STRICT"),
				createSql);
		assertTrue(createSql.contains(
				"DEFAULT ON NULL 'unknown@example.com' NOT NULL"),
				createSql);
		assertTrue(createSql.contains("CONSTRAINT CK_EMAIL_ADDRESS "
				+ "CHECK (REGEXP_LIKE(VALUE, '^[^@]+@[^@]+$')) "
				+ "INITIALLY DEFERRED"), createSql);
		assertTrue(createSql.contains(
				"DISPLAY LOWER(VALUE) ORDER LOWER(VALUE)"), createSql);
		assertTrue(createSql.contains(
				"ANNOTATIONS ( \"Display\" 'Email address')"), createSql);

		final String dropSql = sqlFactoryRegistry
				.createSql(domain, SqlType.DROP).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(dropSql.contains(
				"DROP DOMAIN IF EXISTS EMAIL_ADDRESS"), dropSql);
	}

	@Test
	void testTableAndColumnAnnotationsAndExistenceClauses() {
		final Table table = new Table("CUSTOMERS");
		table.setDialect(dialect);
		final Column id = new Column("ID").setDataType(DataType.INT);
		table.getColumns().add(id);
		OracleAnnotationUtils.setAnnotation(table, "Display",
				"Customer table");
		OracleAnnotationUtils.setAnnotation(id, "Identity", null);
		OracleAnnotationUtils.setAnnotation(id, "Display", "Customer ID");

		final var operations = sqlFactoryRegistry.createSql(table,
				SqlType.CREATE);
		final var operationSql = operations.stream()
				.map(operation -> operation.getSqlText()
						.replaceAll("\\s+", " "))
				.toList();
		final String createSql = operations.get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(createSql.contains(
				"CREATE TABLE IF NOT EXISTS CUSTOMERS"), createSql);
		assertTrue(operationSql.stream().anyMatch(sql -> sql.contains(
						"ALTER TABLE CUSTOMERS ANNOTATIONS "
						+ "( \"Display\" 'Customer table')")),
				operationSql.toString());
		assertTrue(operationSql.stream().anyMatch(sql ->
				sql.contains("ALTER TABLE CUSTOMERS MODIFY ID")
				&& sql.contains("\"Identity\"")
				&& sql.contains("\"Display\" 'Customer ID'")),
				operationSql.toString());

		final String dropSql = sqlFactoryRegistry.createSql(table, SqlType.DROP)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(dropSql.contains("DROP TABLE IF EXISTS CUSTOMERS"), dropSql);
	}

	@Test
	void testRejectCollectionTypeAsDataUseCaseDomain() {
		final Domain domain = new Domain("NUMBER_LIST");
		domain.setDialect(dialect);
		domain.setDataType(DataType.INT);
		domain.setArrayDimension(1);
		domain.setArrayDimensionUpperBound(100);

		assertThrows(IllegalArgumentException.class,
				() -> sqlFactoryRegistry.createSql(domain, SqlType.CREATE));
	}

	@Test
	void testRejectInvalidHybridVectorIndex() {
		final Table table = new Table("DOCUMENTS");
		table.setDialect(dialect);
		table.getColumns().add(new Column("CONTENT").setDataType(DataType.CLOB));
		final Index index = new Index("IDX_DOCUMENTS_HYBRID",
				table.getColumns().get("CONTENT")).setIndexType(IndexType.Vector);
		index.getSpecifics().put(Oracle23aiCreateIndexFactory.HYBRID, true);
		index.getSpecifics().put(Oracle23aiCreateIndexFactory.MODEL, "DOC_MODEL");
		index.getSpecifics().put(Oracle23aiCreateIndexFactory.VECTOR_IDXTYPE, "INVALID");
		table.getIndexes().add(index);

		assertThrows(IllegalArgumentException.class,
				() -> sqlFactoryRegistry.createSql(index, SqlType.CREATE));
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
