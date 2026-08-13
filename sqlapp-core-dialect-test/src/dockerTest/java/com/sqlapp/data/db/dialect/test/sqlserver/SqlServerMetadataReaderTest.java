/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.sqlserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlserver.SqlServer2022;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;

/** SQL Server 2025 integration coverage for the full metadata reader tree. */
class SqlServerMetadataReaderTest {
	private static final String IMAGE =
			"mcr.microsoft.com/mssql/server:2025-latest";

	private static final MSSQLServerContainer SQL_SERVER =
			ReusableTestcontainers.configure(
					new MSSQLServerContainer(IMAGE).acceptLicense());

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(SQL_SERVER);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(SQL_SERVER);
	}

	@Test
	void testReadsRepresentativeSchemaObjectsFromLatestSqlServer()
			throws SQLException {
		try (Connection connection = SQL_SERVER.createConnection("")) {
			createSchemaObjects(connection);

			Dialect dialect = DialectResolver.getInstance()
					.getDialect(connection);
			assertInstanceOf(SqlServer2022.class, dialect,
					"SQL Server 17.x must use the newest supported dialect.");

			Schema schema = SchemaUtils.getSchema(connection, "dbo")
					.orElseThrow(() -> new AssertionError(
							"SQL Server dbo schema was not loaded."));

			Table parent = schema.getTables().get("METADATA_PARENT");
			assertNotNull(parent);
			assertNotNull(parent.getConstraints().get("PK_METADATA_PARENT"));

			Table child = schema.getTables().get("METADATA_CHILD");
			assertNotNull(child);
			assertNotNull(child.getConstraints().get("PK_METADATA_CHILD"));
			assertNotNull(child.getConstraints().get("UK_METADATA_CHILD_CODE"));
			assertNotNull(child.getConstraints().get("CK_METADATA_CHILD_AMOUNT"));
			assertNotNull(child.getConstraints().get("FK_METADATA_CHILD_PARENT"));
			assertNotNull(child.getIndexes().get("IDX_METADATA_CHILD_PARENT"));

			Column id = child.getColumns().get("ID");
			assertTrue(id.isIdentity());
			assertEquals(100L, id.getIdentityStartValue());
			assertEquals(5L, id.getIdentityStep());

			Column code = child.getColumns().get("CODE");
			assertTrue(code.isNotNull());
			assertTrue(code.getDefaultValue().contains("unknown"),
					code.getDefaultValue());

			Column total = child.getColumns().get("TOTAL");
			assertNotNull(total.getFormula());
			assertTrue(total.getFormula().contains("AMOUNT"),
					total.getFormula());
			assertTrue(total.isFormulaPersisted());

			Sequence sequence = schema.getSequences().get("METADATA_SEQ");
			assertNotNull(sequence);
			assertEquals(50L, sequence.getStartValue().longValue());
			assertEquals(10L, sequence.getIncrementBy().longValue());

			assertNotNull(schema.getViews().get("METADATA_VIEW"));
			assertNotNull(schema.getProcedures().get("METADATA_PROCEDURE"));
			assertNotNull(schema.getFunctions().get("METADATA_FUNCTION"));
		}
	}

	private void createSchemaObjects(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("DROP VIEW IF EXISTS METADATA_VIEW");
			statement.execute("DROP PROCEDURE IF EXISTS METADATA_PROCEDURE");
			statement.execute("DROP FUNCTION IF EXISTS METADATA_FUNCTION");
			statement.execute("DROP TABLE IF EXISTS METADATA_CHILD");
			statement.execute("DROP TABLE IF EXISTS METADATA_PARENT");
			statement.execute("DROP SEQUENCE IF EXISTS METADATA_SEQ");
			statement.execute("""
					CREATE SEQUENCE METADATA_SEQ AS BIGINT
					START WITH 50 INCREMENT BY 10
					""");
			statement.execute("""
					CREATE TABLE METADATA_PARENT (
						ID BIGINT NOT NULL,
						NAME NVARCHAR(100) NOT NULL,
						CONSTRAINT PK_METADATA_PARENT PRIMARY KEY (ID)
					)
					""");
			statement.execute("""
					CREATE TABLE METADATA_CHILD (
						ID BIGINT IDENTITY(100, 5) NOT NULL,
						PARENT_ID BIGINT NOT NULL,
						CODE VARCHAR(40) NOT NULL
							CONSTRAINT DF_METADATA_CHILD_CODE DEFAULT 'unknown',
						AMOUNT DECIMAL(18, 2) NOT NULL,
						TOTAL AS (AMOUNT * 2) PERSISTED,
						CONSTRAINT PK_METADATA_CHILD PRIMARY KEY (ID),
						CONSTRAINT UK_METADATA_CHILD_CODE UNIQUE (CODE),
						CONSTRAINT CK_METADATA_CHILD_AMOUNT CHECK (AMOUNT >= 0),
						CONSTRAINT FK_METADATA_CHILD_PARENT FOREIGN KEY (PARENT_ID)
							REFERENCES METADATA_PARENT(ID)
					)
					""");
			statement.execute("""
					CREATE INDEX IDX_METADATA_CHILD_PARENT
					ON METADATA_CHILD(PARENT_ID) INCLUDE (CODE)
					""");
			statement.execute("""
					CREATE VIEW METADATA_VIEW AS
					SELECT ID, CODE, TOTAL FROM METADATA_CHILD
					""");
			statement.execute("""
					CREATE PROCEDURE METADATA_PROCEDURE
						@PARENT_ID BIGINT
					AS
					SELECT ID, CODE FROM METADATA_CHILD
					WHERE PARENT_ID = @PARENT_ID
					""");
			statement.execute("""
					CREATE FUNCTION METADATA_FUNCTION(@AMOUNT DECIMAL(18, 2))
					RETURNS DECIMAL(18, 2)
					AS
					BEGIN
						RETURN @AMOUNT * 2
					END
					""");
		}
	}
}
