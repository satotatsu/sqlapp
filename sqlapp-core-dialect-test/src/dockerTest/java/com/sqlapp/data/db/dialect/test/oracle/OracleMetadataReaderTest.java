/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.oracle.OracleContainer;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.UniqueConstraint;

/** Oracle Database integration coverage for the metadata reader tree. */
class OracleMetadataReaderTest {
	private static final OracleContainer ORACLE = ReusableTestcontainers
			.configure(new OracleContainer(OracleTestEnvironment.image()));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(ORACLE);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(ORACLE);
	}

	@Test
	void partitionMetadataUsesConstantQueryCounts() throws Exception {
		try (Connection connection = ORACLE.createConnection("")) {
			OracleMetadataAuditAssertions.verify(connection);
			com.sqlapp.data.db.dialect.test.SequenceMetadataAuditAssertions.verify(connection, true);
		}
	}

	@Test
	void generatedRoutineArgumentsSurviveMetadataXmlAndRecreation() throws Exception {
		try (Connection connection = ORACLE.createConnection("")) {
			Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			var registry = dialect.createSqlFactoryRegistry();
			var procedure = new com.sqlapp.data.schemas.Procedure("ROUNDTRIP_ARGUMENTS");
			procedure.setStatement("IS BEGIN RESULT := \"input value\"; STATE := STATE + 1; END;");
			procedure.getArguments().add(new com.sqlapp.data.schemas.NamedArgument("input value")
					.setDataType(DataType.INT).setDefaultValue("42"));
			var output = new com.sqlapp.data.schemas.NamedArgument("RESULT").setDataType(DataType.INT);
			output.setDirection(com.sqlapp.jdbc.sql.ParameterDirection.Output);
			procedure.getArguments().add(output);
			var inout = new com.sqlapp.data.schemas.NamedArgument("STATE").setDataType(DataType.INT);
			inout.setDirection(com.sqlapp.jdbc.sql.ParameterDirection.Inout);
			procedure.getArguments().add(inout);
			var function = new com.sqlapp.data.schemas.Function("ROUNDTRIP_DEFAULT");
			function.getReturning().setDataType(DataType.INT);
			function.getArguments().add(
					new com.sqlapp.data.schemas.NamedArgument("N").setDataType(DataType.INT).setDefaultValue("1 + 2"));
			function.setStatement("IS BEGIN RETURN N * 2; END;");
			try (Statement sql = connection.createStatement()) {
				for (var operation : registry.createSql(procedure, com.sqlapp.data.db.sql.SqlType.CREATE))
					sql.execute(operation.getSqlText());
				for (var operation : registry.createSql(function, com.sqlapp.data.db.sql.SqlType.CREATE))
					sql.execute(operation.getSqlText());
				var schemaReader = dialect.getCatalogReader().getSchemaReader();
				var procedureReader = schemaReader.getProcedureReader();
				procedureReader.setSchemaName(connection.getMetaData().getUserName().toUpperCase(Locale.ROOT));
				procedureReader.setObjectName(procedure.getName());
				var savedProcedure = procedureReader.getAllFull(connection).get(0);
				assertEquals(3, savedProcedure.getArguments().size());
				assertNotNull(savedProcedure.getDefinition());
				com.sqlapp.data.schemas.Procedure restoredProcedure = com.sqlapp.data.schemas.SchemaUtils
						.readXml(new java.io.StringReader(savedProcedure.asXml()));
				var functionReader = schemaReader.getFunctionReader();
				functionReader.setSchemaName(savedProcedure.getSchemaName());
				functionReader.setObjectName(function.getName());
				var savedFunction = functionReader.getAllFull(connection).get(0);
				assertNotNull(savedFunction.getDefinition());
				com.sqlapp.data.schemas.Function restoredFunction = com.sqlapp.data.schemas.SchemaUtils
						.readXml(new java.io.StringReader(savedFunction.asXml()));
				sql.execute("DROP PROCEDURE ROUNDTRIP_ARGUMENTS");
				sql.execute("DROP FUNCTION ROUNDTRIP_DEFAULT");
				for (var operation : registry.createSql(restoredProcedure, com.sqlapp.data.db.sql.SqlType.CREATE))
					sql.execute(operation.getSqlText());
				for (var operation : registry.createSql(restoredFunction, com.sqlapp.data.db.sql.SqlType.CREATE))
					sql.execute(operation.getSqlText());
				try (var call = connection.prepareCall("BEGIN ROUNDTRIP_ARGUMENTS(RESULT => ?, STATE => ?); END;")) {
					call.registerOutParameter(1, java.sql.Types.NUMERIC);
					call.setInt(2, 9);
					call.registerOutParameter(2, java.sql.Types.NUMERIC);
					call.execute();
					assertEquals(42, call.getInt(1));
					assertEquals(10, call.getInt(2));
				}
				try (var result = sql.executeQuery("SELECT ROUNDTRIP_DEFAULT() FROM DUAL")) {
					assertTrue(result.next());
					assertEquals(6, result.getInt(1));
				}
			} finally {
				try (Statement sql = connection.createStatement()) {
					drop(sql, "DROP PROCEDURE ROUNDTRIP_ARGUMENTS", 4043);
					drop(sql, "DROP FUNCTION ROUNDTRIP_DEFAULT", 4043);
				}
			}
		}
	}

	@Test
	void testReadsRepresentativeSchemaObjectsFromLatestOracle() throws SQLException {
		try (Connection connection = ORACLE.createConnection("")) {
			grantDirectoryAccess();
			createSchemaObjects(connection);

			Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			OracleTestEnvironment.assertExpectedDialect(dialect);

			String schemaName = ORACLE.getUsername().toUpperCase(Locale.ROOT);
			var schemaReader = dialect.getCatalogReader().getSchemaReader();
			schemaReader.setSchemaName(schemaName);
			Schema schema = schemaReader.getAllFull(connection).stream()
					.filter(current -> schemaName.equalsIgnoreCase(current.getName())).findFirst()
					.orElseThrow(() -> new AssertionError("Oracle test schema was not loaded."));

			Table parent = schema.getTables().get("METADATA_PARENT");
			assertNotNull(parent);
			assertEquals("Metadata parent table", parent.getRemarks());
			UniqueConstraint primaryKey = assertInstanceOf(UniqueConstraint.class,
					parent.getConstraints().get("PK_METADATA_PARENT"));
			assertEquals("ID", primaryKey.getColumns().get(0).getName());
			assertEquals("REGION", primaryKey.getColumns().get(1).getName());
			assertEquals("Display name", parent.getColumns().get("NAME").getRemarks());

			Table child = schema.getTables().get("METADATA_CHILD");
			assertNotNull(child);
			assertNotNull(child.getConstraints().get("PK_METADATA_CHILD"));
			assertNotNull(child.getConstraints().get("UK_METADATA_CHILD_CODE"));
			assertNotNull(child.getConstraints().get("CK_METADATA_CHILD_AMOUNT"));
			ForeignKeyConstraint foreignKey = assertInstanceOf(ForeignKeyConstraint.class,
					child.getConstraints().get("FK_METADATA_CHILD_PARENT"));
			assertTrue(foreignKey.isEnable());
			assertEquals("PARENT_ID", foreignKey.getColumns().get(0).getName());
			assertEquals("ID", foreignKey.getRelatedColumns().get(0).getName());

			Index index = child.getIndexes().get("IDX_METADATA_CHILD_PARENT");
			assertNotNull(index);
			assertEquals("PARENT_ID", index.getColumns().get(0).getName());
			assertEquals("CODE", index.getColumns().get(1).getName());
			Index functionIndex = parent.getIndexes().get("IDX_METADATA_PARENT_UPPER_NAME");
			assertNotNull(functionIndex);
			assertTrue(functionIndex.getColumns().get(0).getName().contains("UPPER"));

			Table partitioned = schema.getTables().get("METADATA_PARTITIONED");
			assertNotNull(partitioned);
			assertEquals(PartitioningType.Range, partitioned.getPartitioning().getPartitioningType());
			assertEquals("CREATED_AT", partitioned.getPartitioning().getPartitioningColumns().get(0).getName());
			assertEquals(PartitioningType.Hash, partitioned.getPartitioning().getSubPartitioningType());
			assertEquals("REGION", partitioned.getPartitioning().getSubPartitioningColumns().get(0).getName());
			assertEquals(2, partitioned.getPartitioning().getPartitions().size());
			assertEquals(2, partitioned.getPartitioning().getPartitions().get("P_2025").getSubPartitions().size());
			Index localIndex = partitioned.getIndexes().get("IDX_METADATA_PARTITIONED_REGION");
			assertNotNull(localIndex);
			assertEquals(2, localIndex.getPartitioning().getPartitions().size());

			assertTrue(child.getColumns().get("ID").isIdentity());
			assertTrue(child.getColumns().get("CODE").isNotNull());
			assertTrue(child.getColumns().get("CODE").getDefaultValue().contains("unknown"));
			assertNotNull(child.getColumns().get("TOTAL"));

			Table vectorTable = schema.getTables().get("METADATA_VECTOR");
			assertNotNull(vectorTable);
			assertEquals(DataType.VECTOR, vectorTable.getColumns().get("EMBEDDING").getDataType());

			var externalTable = schema.getExternalTables().get("METADATA_EXTERNAL");
			assertNotNull(externalTable);
			assertEquals("DATA_PUMP_DIR", externalTable.getDefaultDirectoryName());
			assertEquals("DATA_PUMP_DIR", externalTable.getDirectoryName());
			assertEquals("metadata_external.csv", externalTable.getLocation());
			assertEquals("ORACLE_LOADER", externalTable.getTypeName());
			assertEquals("CLOB", externalTable.getAccessType());
			assertEquals("10", externalTable.getRejectLimit());
			assertTrue(externalTable.getAccessParameters().contains("TERMINATED BY"));

			var dimension = schema.getDimensions().get("METADATA_DIMENSION");
			assertNotNull(dimension);
			assertEquals(schemaName, dimension.getSchemaName());
			assertEquals("VALID", dimension.getSpecifics().get("COMPILE_STATE"));
			var productLevel = dimension.getLevels().get("PRODUCT");
			assertNotNull(productLevel);
			assertEquals("METADATA_DIM_PRODUCT", productLevel.getColumns().get(0).getTableName());
			assertEquals("PRODUCT_ID", productLevel.getColumns().get(0).getName());
			assertNotNull(dimension.getLevels().get("CATEGORY"));
			var hierarchy = dimension.getHierarchies().get("PRODUCT_ROLLUP");
			assertNotNull(hierarchy);
			assertEquals("PRODUCT", hierarchy.getLevels().get(0).getName());
			assertEquals("CATEGORY", hierarchy.getLevels().get(1).getName());
			var attribute = dimension.getAttributes().get("PRODUCT");
			assertNotNull(attribute);
			assertEquals("PRODUCT_NAME", attribute.getColumns().get(0).getName());

			Sequence sequence = schema.getSequences().get("METADATA_SEQ");
			assertNotNull(sequence);

			var view = schema.getViews().get("METADATA_VIEW");
			assertNotNull(view);
			assertNotNull(view.getColumns().get("TOTAL"));
			assertTrue(String.join("\n", view.getStatement()).toUpperCase(Locale.ROOT).contains("METADATA_CHILD"));
			var mview = schema.getMviews().get("METADATA_MVIEW");
			assertNotNull(mview);
			assertEquals("Materialized audit comment", mview.getRemarks());
			assertNotNull(mview.getStatement());
			assertTrue(mview.getStatement().toString().contains("METADATA_PARENT"));
			var mviewLog = schema.getMviewLogs().stream()
					.filter(current -> "METADATA_PARENT".equals(current.getMasterTableName())).findFirst()
					.orElseThrow();
			assertTrue(mviewLog.isSavePrimaryKey());
			assertTrue(mviewLog.isSaveRowIds());
			assertTrue(mviewLog.isIncludeNewValues());
			var procedure = schema.getProcedures().get("METADATA_PROCEDURE");
			assertNotNull(procedure);
			assertEquals(1, procedure.getArguments().size());
			assertEquals("P_PARENT_ID", procedure.getArguments().get(0).getName());
			assertTrue(
					String.join("\n", procedure.getDefinition()).toUpperCase(Locale.ROOT).contains("METADATA_CHILD"));
			var function = schema.getFunctions().get("METADATA_FUNCTION");
			assertNotNull(function);
			assertEquals(1, function.getArguments().size());
			assertEquals("P_AMOUNT", function.getArguments().get(0).getName());
			assertTrue(String.join("\n", function.getDefinition()).toUpperCase(Locale.ROOT).contains("P_AMOUNT * 2"));
			Trigger trigger = schema.getTriggers().get("METADATA_TRIGGER");
			assertNotNull(trigger);
			assertEquals("METADATA_CHILD", trigger.getTableName());
			assertEquals("BEFORE", trigger.getActionTiming());
			assertTrue(trigger.getEventManipulation().contains("INSERT"));
			assertTrue(String.join("\n", trigger.getStatement()).toUpperCase(Locale.ROOT).contains("UPPER"));
			assertNotNull(schema.getSynonyms().get("METADATA_PARENT_SYNONYM"));
			var metadataPackage = schema.getPackages().get("METADATA_PACKAGE");
			assertNotNull(metadataPackage);
			assertNotNull(metadataPackage.getDefinition());
			assertTrue(metadataPackage.getDefinition().stream().anyMatch(line -> line.contains("DOUBLE_AMOUNT")));
			assertNotNull(metadataPackage.getBody());
			assertNotNull(metadataPackage.getBody().getDefinition());
			assertTrue(metadataPackage.getBody().getDefinition().stream()
					.anyMatch(line -> line.contains("RETURN P_AMOUNT * 2")));
		}
	}

	@Test
	void recreatesIotAndReadOnlyViewThroughMetadataXml() throws Exception {
		try (Connection connection = ORACLE.createConnection(""); Statement sql = connection.createStatement()) {
			try {
				sql.execute(
						"CREATE TABLE ROUNDTRIP_IOT (ID NUMBER PRIMARY KEY, VALUE VARCHAR2(4000)) ORGANIZATION INDEX OVERFLOW");
				sql.execute(
						"CREATE VIEW ROUNDTRIP_VIEW (ITEM_ID, ITEM_VALUE) AS SELECT ID, VALUE FROM ROUNDTRIP_IOT WITH READ ONLY CONSTRAINT ROUNDTRIP_RO");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				String owner = connection.getMetaData().getUserName().toUpperCase(Locale.ROOT);
				var schemaReader = dialect.getCatalogReader().getSchemaReader();
				var tableReader = schemaReader.getTableReader();
				tableReader.setSchemaName(owner);
				tableReader.setObjectName("ROUNDTRIP_IOT");
				Table table = com.sqlapp.data.schemas.SchemaUtils
						.readXml(new java.io.StringReader(tableReader.getAllFull(connection).get(0).asXml()));
				var viewReader = schemaReader.getViewReader();
				viewReader.setSchemaName(owner);
				viewReader.setObjectName("ROUNDTRIP_VIEW");
				com.sqlapp.data.schemas.View view = com.sqlapp.data.schemas.SchemaUtils
						.readXml(new java.io.StringReader(viewReader.getAllFull(connection).get(0).asXml()));
				assertEquals("ROUNDTRIP_RO", view.getSpecifics().get("oracle.view.readOnlyConstraint"));
				sql.execute("DROP VIEW ROUNDTRIP_VIEW");
				sql.execute("DROP TABLE ROUNDTRIP_IOT PURGE");
				var registry = dialect.createSqlFactoryRegistry();
				for (var operation : registry.createSql(table, com.sqlapp.data.db.sql.SqlType.CREATE))
					sql.execute(operation.getSqlText());
				for (var operation : registry.createSql(view, com.sqlapp.data.db.sql.SqlType.CREATE))
					sql.execute(operation.getSqlText());
				sql.execute("INSERT INTO ROUNDTRIP_IOT VALUES (1, 'restored')");
				try (var result = sql.executeQuery("SELECT ITEM_VALUE FROM ROUNDTRIP_VIEW WHERE ITEM_ID = 1")) {
					assertTrue(result.next());
					assertEquals("restored", result.getString(1));
				}
				assertEquals("ROUNDTRIP_RO",
						viewReader.getAllFull(connection).get(0).getSpecifics().get("oracle.view.readOnlyConstraint"));
				try (var result = sql.executeQuery(
						"SELECT IOT_TYPE, IOT_NAME FROM USER_TABLES WHERE TABLE_NAME = 'ROUNDTRIP_IOT' OR IOT_NAME = 'ROUNDTRIP_IOT'")) {
					int count = 0;
					while (result.next()) {
						assertTrue("IOT".equals(result.getString(1)) || "IOT_OVERFLOW".equals(result.getString(1)));
						count++;
					}
					assertEquals(2, count);
				}
			} finally {
				drop(sql, "DROP VIEW ROUNDTRIP_VIEW", 942);
				drop(sql, "DROP TABLE ROUNDTRIP_IOT PURGE", 942);
			}
		}
	}

	@Test
	void retainsMviewQueryWithoutCatalogRoleAndReadsDdlAfterGrant() throws Exception {
		try (Connection admin = DriverManager.getConnection(ORACLE.getJdbcUrl(), "system", ORACLE.getPassword());
				Statement sql = admin.createStatement();
				Connection connection = ORACLE.createConnection("")) {
			String user = ORACLE.getUsername().toUpperCase(Locale.ROOT);
			boolean roleGranted = false;
			try {
				sql.execute("CREATE TABLE SYSTEM.ROUNDTRIP_MV_BASE (ID NUMBER)");
				sql.execute("CREATE MATERIALIZED VIEW SYSTEM.ROUNDTRIP_MV AS SELECT ID FROM SYSTEM.ROUNDTRIP_MV_BASE");
				sql.execute("GRANT SELECT ON SYSTEM.ROUNDTRIP_MV TO " + user);
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getMviewReader();
				reader.setSchemaName("SYSTEM");
				reader.setObjectName("ROUNDTRIP_MV");
				var limited = reader.getAll(connection);
				assertEquals(1, limited.size());
				assertTrue(limited.get(0).getDefinition() == null || limited.get(0).getDefinition().isEmpty());
				assertTrue(String.join("\n", limited.get(0).getStatement()).contains("ROUNDTRIP_MV_BASE"));
				sql.execute("GRANT SELECT_CATALOG_ROLE TO " + user);
				roleGranted = true;
				try (Connection privileged = ORACLE.createConnection("")) {
					var complete = reader.getAll(privileged);
					assertEquals(1, complete.size());
					assertNotNull(complete.get(0).getDefinition());
					assertTrue(String.join("\n", complete.get(0).getDefinition()).contains("CREATE MATERIALIZED VIEW"));
				}
			} finally {
				if (roleGranted)
					sql.execute("REVOKE SELECT_CATALOG_ROLE FROM " + user);
				drop(sql, "DROP MATERIALIZED VIEW SYSTEM.ROUNDTRIP_MV", 12003);
				drop(sql, "DROP TABLE SYSTEM.ROUNDTRIP_MV_BASE PURGE", 942);
			}
		}
	}

	private void createSchemaObjects(final Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			drop(statement, "DROP SYNONYM METADATA_PARENT_SYNONYM", 1434);
			drop(statement, "DROP TRIGGER METADATA_TRIGGER", 4080);
			drop(statement, "DROP MATERIALIZED VIEW METADATA_MVIEW", 12003);
			drop(statement, "DROP MATERIALIZED VIEW LOG ON METADATA_PARENT", 942, 12002);
			drop(statement, "DROP VIEW METADATA_VIEW", 942);
			drop(statement, "DROP PROCEDURE METADATA_PROCEDURE", 4043);
			drop(statement, "DROP FUNCTION METADATA_FUNCTION", 4043);
			drop(statement, "DROP PACKAGE METADATA_PACKAGE", 4043);
			drop(statement, "DROP DIMENSION METADATA_DIMENSION", 30333);
			drop(statement, "DROP TABLE METADATA_EXTERNAL PURGE", 942);
			drop(statement, "DROP TABLE METADATA_DIM_PRODUCT PURGE", 942);
			drop(statement, "DROP TABLE METADATA_CHILD CASCADE CONSTRAINTS PURGE", 942);
			drop(statement, "DROP TABLE METADATA_VECTOR PURGE", 942);
			drop(statement, "DROP TABLE METADATA_PARTITIONED PURGE", 942);
			drop(statement, "DROP TABLE METADATA_PARENT CASCADE CONSTRAINTS PURGE", 942);
			drop(statement, "DROP SEQUENCE METADATA_SEQ", 2289);
			statement.execute("""
					CREATE SEQUENCE METADATA_SEQ START WITH 50 INCREMENT BY 10
					""");
			statement.execute("""
					CREATE TABLE METADATA_PARENT (
						ID NUMBER(19) NOT NULL,
						REGION CHAR(2) NOT NULL,
						NAME VARCHAR2(100) NOT NULL,
						CONSTRAINT PK_METADATA_PARENT PRIMARY KEY (ID, REGION)
					)
					""");
			statement.execute("""
					CREATE TABLE METADATA_CHILD (
						ID NUMBER(19) GENERATED BY DEFAULT AS IDENTITY,
						PARENT_ID NUMBER(19) NOT NULL,
						PARENT_REGION CHAR(2) NOT NULL,
						CODE VARCHAR2(40) DEFAULT 'unknown' NOT NULL,
						AMOUNT NUMBER(18, 2) NOT NULL,
						TOTAL NUMBER(19, 2) GENERATED ALWAYS AS (AMOUNT * 2) VIRTUAL,
						CONSTRAINT PK_METADATA_CHILD PRIMARY KEY (ID),
						CONSTRAINT UK_METADATA_CHILD_CODE UNIQUE (CODE),
						CONSTRAINT CK_METADATA_CHILD_AMOUNT CHECK (AMOUNT >= 0),
						CONSTRAINT FK_METADATA_CHILD_PARENT
							FOREIGN KEY (PARENT_ID, PARENT_REGION)
							REFERENCES METADATA_PARENT(ID, REGION)
					)
					""");
			statement.execute("""
					CREATE INDEX IDX_METADATA_CHILD_PARENT
					ON METADATA_CHILD(PARENT_ID DESC, CODE ASC)
					""");
			statement.execute("""
					CREATE INDEX IDX_METADATA_PARENT_UPPER_NAME
					 ON METADATA_PARENT(UPPER(NAME))
					""");
			statement.execute("""
					CREATE TABLE METADATA_VECTOR (
						ID NUMBER(19) PRIMARY KEY,
						EMBEDDING VECTOR(3, FLOAT32)
					)
					""");
			statement.execute("""
					CREATE TABLE METADATA_EXTERNAL (
					 ID NUMBER(19), NAME VARCHAR2(100))
					 ORGANIZATION EXTERNAL (
					  TYPE ORACLE_LOADER
					  DEFAULT DIRECTORY DATA_PUMP_DIR
					  ACCESS PARAMETERS (
					   RECORDS DELIMITED BY NEWLINE
					   FIELDS TERMINATED BY ',')
					  LOCATION ('metadata_external.csv'))
					 REJECT LIMIT 10
					""");
			statement.execute("""
					CREATE TABLE METADATA_DIM_PRODUCT (
					 PRODUCT_ID NUMBER(19) NOT NULL,
					 CATEGORY_ID NUMBER(19) NOT NULL,
					 PRODUCT_NAME VARCHAR2(100))
					""");
			statement.execute("""
					CREATE DIMENSION METADATA_DIMENSION
					 LEVEL PRODUCT IS (METADATA_DIM_PRODUCT.PRODUCT_ID)
					 LEVEL CATEGORY IS (METADATA_DIM_PRODUCT.CATEGORY_ID)
					 HIERARCHY PRODUCT_ROLLUP (PRODUCT CHILD OF CATEGORY)
					 ATTRIBUTE PRODUCT DETERMINES
					  (METADATA_DIM_PRODUCT.PRODUCT_NAME)
					""");
			statement.execute("""
					CREATE TABLE METADATA_PARTITIONED (
					 ID NUMBER(19) NOT NULL,
					 REGION CHAR(2) NOT NULL,
					 CREATED_AT DATE NOT NULL,
					 PAYLOAD VARCHAR2(100)
					)
					PARTITION BY RANGE (CREATED_AT)
					SUBPARTITION BY HASH (REGION) SUBPARTITIONS 2 (
					 PARTITION P_2025 VALUES LESS THAN (DATE '2026-01-01'),
					 PARTITION P_MAX VALUES LESS THAN (MAXVALUE)
					)
					""");
			statement.execute("""
					CREATE INDEX IDX_METADATA_PARTITIONED_REGION
					 ON METADATA_PARTITIONED(REGION) LOCAL
					""");
			statement.execute("COMMENT ON TABLE METADATA_PARENT IS 'Metadata parent table'");
			statement.execute("COMMENT ON COLUMN METADATA_PARENT.NAME IS 'Display name'");
			statement.execute("""
					CREATE MATERIALIZED VIEW LOG ON METADATA_PARENT
					 WITH PRIMARY KEY, ROWID INCLUDING NEW VALUES
					""");
			statement.execute("""
					CREATE MATERIALIZED VIEW METADATA_MVIEW
					 BUILD IMMEDIATE REFRESH COMPLETE ON DEMAND AS
					 SELECT ID, REGION, NAME FROM METADATA_PARENT
					""");
			statement.execute("COMMENT ON MATERIALIZED VIEW METADATA_MVIEW IS 'Materialized audit comment'");
			statement.execute("""
					CREATE VIEW METADATA_VIEW AS
					SELECT ID, CODE, TOTAL FROM METADATA_CHILD
					""");
			statement.execute("""
					CREATE PROCEDURE METADATA_PROCEDURE(P_PARENT_ID IN NUMBER) AS
					BEGIN
						UPDATE METADATA_CHILD SET AMOUNT = AMOUNT
						 WHERE PARENT_ID = P_PARENT_ID;
					END;
					""");
			statement.execute("""
					CREATE FUNCTION METADATA_FUNCTION(P_AMOUNT IN NUMBER)
					RETURN NUMBER AS
					BEGIN
						RETURN P_AMOUNT * 2;
					END;
					""");
			statement.execute("""
					CREATE PACKAGE METADATA_PACKAGE AS
					 FUNCTION DOUBLE_AMOUNT(P_AMOUNT IN NUMBER) RETURN NUMBER;
					 PROCEDURE TOUCH_PARENT(P_PARENT_ID IN NUMBER);
					END METADATA_PACKAGE;
					""");
			statement.execute("""
					CREATE PACKAGE BODY METADATA_PACKAGE AS
					 FUNCTION DOUBLE_AMOUNT(P_AMOUNT IN NUMBER) RETURN NUMBER AS
					 BEGIN
					  RETURN P_AMOUNT * 2;
					 END DOUBLE_AMOUNT;
					 PROCEDURE TOUCH_PARENT(P_PARENT_ID IN NUMBER) AS
					 BEGIN
					  NULL;
					 END TOUCH_PARENT;
					END METADATA_PACKAGE;
					""");
			statement.execute("""
					CREATE TRIGGER METADATA_TRIGGER
					BEFORE INSERT ON METADATA_CHILD
					FOR EACH ROW
					BEGIN
						:NEW.CODE := UPPER(:NEW.CODE);
					END;
					""");
			statement.execute("""
					CREATE SYNONYM METADATA_PARENT_SYNONYM FOR METADATA_PARENT
					""");
		}
	}

	private void grantDirectoryAccess() throws SQLException {
		try (Connection connection = DriverManager.getConnection(ORACLE.getJdbcUrl(), "system", ORACLE.getPassword());
				Statement statement = connection.createStatement()) {
			statement.execute(
					"GRANT READ, WRITE ON DIRECTORY DATA_PUMP_DIR TO " + ORACLE.getUsername().toUpperCase(Locale.ROOT));
		}
	}

	private void drop(final Statement statement, final String sql, final int... missingObjectErrorCodes)
			throws SQLException {
		try {
			statement.execute(sql);
		} catch (SQLException e) {
			for (int missingObjectErrorCode : missingObjectErrorCodes) {
				if (e.getErrorCode() == missingObjectErrorCode) {
					return;
				}
			}
			throw e;
		}
	}
}
