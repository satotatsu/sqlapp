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
import com.sqlapp.data.db.dialect.oracle.Oracle23ai;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.UniqueConstraint;

/** Oracle Database 23ai integration coverage for the metadata reader tree. */
class OracleMetadataReaderTest {
	private static final String IMAGE = "gvenzl/oracle-free:23-slim-faststart";

	private static final OracleContainer ORACLE =
			ReusableTestcontainers.configure(new OracleContainer(IMAGE));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(ORACLE);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(ORACLE);
	}

	@Test
	void testReadsRepresentativeSchemaObjectsFromLatestOracle()
			throws SQLException {
		try (Connection connection = ORACLE.createConnection("")) {
			createSchemaObjects(connection);

			Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(Oracle23ai.class, dialect);

			String schemaName = ORACLE.getUsername().toUpperCase(Locale.ROOT);
			var schemaReader = dialect.getCatalogReader().getSchemaReader();
			schemaReader.setSchemaName(schemaName);
			Schema schema = schemaReader.getAllFull(connection).stream()
					.filter(current -> schemaName.equalsIgnoreCase(current.getName()))
					.findFirst()
					.orElseThrow(() -> new AssertionError(
							"Oracle test schema was not loaded."));

			Table parent = schema.getTables().get("METADATA_PARENT");
			assertNotNull(parent);
			assertEquals("Metadata parent table", parent.getRemarks());
			UniqueConstraint primaryKey = assertInstanceOf(
					UniqueConstraint.class,
					parent.getConstraints().get("PK_METADATA_PARENT"));
			assertEquals("ID", primaryKey.getColumns().get(0).getName());
			assertEquals("REGION", primaryKey.getColumns().get(1).getName());
			assertEquals("Display name",
					parent.getColumns().get("NAME").getRemarks());

			Table child = schema.getTables().get("METADATA_CHILD");
			assertNotNull(child);
			assertNotNull(child.getConstraints().get("PK_METADATA_CHILD"));
			assertNotNull(child.getConstraints().get("UK_METADATA_CHILD_CODE"));
			assertNotNull(child.getConstraints().get("CK_METADATA_CHILD_AMOUNT"));
			ForeignKeyConstraint foreignKey = assertInstanceOf(
					ForeignKeyConstraint.class,
					child.getConstraints().get("FK_METADATA_CHILD_PARENT"));
			assertEquals("PARENT_ID", foreignKey.getColumns().get(0).getName());
			assertEquals("ID", foreignKey.getRelatedColumns().get(0).getName());

			Index index = child.getIndexes().get("IDX_METADATA_CHILD_PARENT");
			assertNotNull(index);
			assertEquals("PARENT_ID", index.getColumns().get(0).getName());
			assertEquals("CODE", index.getColumns().get(1).getName());

			Table partitioned = schema.getTables().get("METADATA_PARTITIONED");
			assertNotNull(partitioned);
			assertEquals(PartitioningType.Range,
					partitioned.getPartitioning().getPartitioningType());
			assertEquals("CREATED_AT", partitioned.getPartitioning()
					.getPartitioningColumns().get(0).getName());
			assertEquals(PartitioningType.Hash,
					partitioned.getPartitioning().getSubPartitioningType());
			assertEquals("REGION", partitioned.getPartitioning()
					.getSubPartitioningColumns().get(0).getName());
			assertEquals(2, partitioned.getPartitioning().getPartitions().size());
			assertEquals(2, partitioned.getPartitioning().getPartitions()
					.get("P_2025").getSubPartitions().size());
			Index localIndex = partitioned.getIndexes()
					.get("IDX_METADATA_PARTITIONED_REGION");
			assertNotNull(localIndex);
			assertEquals(2, localIndex.getPartitioning().getPartitions().size());

			assertTrue(child.getColumns().get("ID").isIdentity());
			assertTrue(child.getColumns().get("CODE").isNotNull());
			assertTrue(child.getColumns().get("CODE").getDefaultValue()
					.contains("unknown"));
			assertNotNull(child.getColumns().get("TOTAL"));

			Table vectorTable = schema.getTables().get("METADATA_VECTOR");
			assertNotNull(vectorTable);
			assertEquals(DataType.VECTOR,
					vectorTable.getColumns().get("EMBEDDING").getDataType());

			Sequence sequence = schema.getSequences().get("METADATA_SEQ");
			assertNotNull(sequence);

			assertNotNull(schema.getViews().get("METADATA_VIEW"));
			var procedure = schema.getProcedures().get("METADATA_PROCEDURE");
			assertNotNull(procedure);
			assertEquals(1, procedure.getArguments().size());
			assertEquals("P_PARENT_ID", procedure.getArguments().get(0).getName());
			assertNotNull(procedure.getStatement());
			var function = schema.getFunctions().get("METADATA_FUNCTION");
			assertNotNull(function);
			assertEquals(1, function.getArguments().size());
			assertEquals("P_AMOUNT", function.getArguments().get(0).getName());
			assertNotNull(function.getStatement());
			Trigger trigger = schema.getTriggers().get("METADATA_TRIGGER");
			assertNotNull(trigger);
			assertNotNull(schema.getSynonyms().get("METADATA_PARENT_SYNONYM"));
		}
	}

	private void createSchemaObjects(final Connection connection)
			throws SQLException {
		try (Statement statement = connection.createStatement()) {
			drop(statement, "DROP SYNONYM METADATA_PARENT_SYNONYM", 1434);
			drop(statement, "DROP TRIGGER METADATA_TRIGGER", 4080);
			drop(statement, "DROP VIEW METADATA_VIEW", 942);
			drop(statement, "DROP PROCEDURE METADATA_PROCEDURE", 4043);
			drop(statement, "DROP FUNCTION METADATA_FUNCTION", 4043);
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
					CREATE TABLE METADATA_VECTOR (
						ID NUMBER(19) PRIMARY KEY,
						EMBEDDING VECTOR(3, FLOAT32)
					)
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
					CREATE VIEW METADATA_VIEW AS
					SELECT ID, CODE, TOTAL FROM METADATA_CHILD
					""");
			statement.execute("""
					CREATE PROCEDURE METADATA_PROCEDURE(P_PARENT_ID IN NUMBER) AS
					BEGIN
						NULL;
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
					CREATE TRIGGER METADATA_TRIGGER
					BEFORE INSERT ON METADATA_CHILD
					FOR EACH ROW
					BEGIN
						NULL;
					END;
					""");
			statement.execute("""
					CREATE SYNONYM METADATA_PARENT_SYNONYM FOR METADATA_PARENT
					""");
		}
	}

	private void drop(final Statement statement, final String sql,
			final int missingObjectErrorCode) throws SQLException {
		try {
			statement.execute(sql);
		} catch (SQLException e) {
			if (e.getErrorCode() != missingObjectErrorCode) {
				throw e;
			}
		}
	}
}
