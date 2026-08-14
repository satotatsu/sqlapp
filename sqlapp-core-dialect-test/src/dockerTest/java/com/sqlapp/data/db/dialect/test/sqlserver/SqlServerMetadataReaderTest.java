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
import java.sql.DriverManager;
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
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.PartitionFunction;
import com.sqlapp.data.schemas.PartitionScheme;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.data.schemas.UniqueConstraint;

/** SQL Server 2025 integration coverage for the full metadata reader tree. */
class SqlServerMetadataReaderTest {
	private static final String IMAGE =
			"mcr.microsoft.com/mssql/server:2025-latest";
	private static final String DATABASE_NAME = "METADATA_READER_TEST";

	private static final MSSQLServerContainer SQL_SERVER =
			ReusableTestcontainers.configure(
					new MSSQLServerContainer(IMAGE).acceptLicense());

	@BeforeAll
	static void startContainer() throws SQLException {
		ReusableTestcontainers.start(SQL_SERVER);
		try (Connection connection = SQL_SERVER.createConnection("");
				Statement statement = connection.createStatement()) {
			statement.execute("IF DB_ID(N'" + DATABASE_NAME
					+ "') IS NULL CREATE DATABASE " + DATABASE_NAME);
		}
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(SQL_SERVER);
	}

	@Test
	void testReadsRepresentativeSchemaObjectsFromLatestSqlServer()
			throws SQLException {
		try (Connection connection = createTestDatabaseConnection()) {
			createSchemaObjects(connection);

			Dialect dialect = DialectResolver.getInstance()
					.getDialect(connection);
			assertInstanceOf(SqlServer2022.class, dialect,
					"SQL Server 17.x must use the newest supported dialect.");

			var catalogReader = dialect.getCatalogReader();
			catalogReader.setCatalogName(connection.getCatalog());
			Catalog catalog = catalogReader.getAllFull(connection).stream()
					.filter(current -> connectionCatalogEquals(connection, current))
					.findFirst()
					.orElseThrow(() -> new AssertionError(
							"Current SQL Server catalog was not loaded."));
			Schema schema = catalog.getSchemas().get("dbo");
			assertNotNull(schema, "SQL Server dbo schema was not loaded.");
			Schema auxiliarySchema = catalog.getSchemas().get("METADATA_AUX");
			assertNotNull(auxiliarySchema);
			Table auxiliaryParent = auxiliarySchema.getTables()
					.get("METADATA_PARENT");
			assertNotNull(auxiliaryParent);
			assertNotNull(auxiliaryParent.getColumns().get("AUXILIARY_VALUE"));
			assertEquals(2, auxiliaryParent.getColumns().size());
			assertNotNull(auxiliaryParent.getConstraints()
					.get("PK_METADATA_AUX_PARENT"));
			assertNotNull(auxiliaryParent.getIndexes()
					.get("IDX_METADATA_AUX_PARENT"));

			PartitionFunction partitionFunction = catalog.getPartitionFunctions()
					.get("METADATA_PARTITION_FUNCTION");
			assertNotNull(partitionFunction);
			assertTrue(partitionFunction.isBoundaryValueOnRight());
			assertEquals(3, partitionFunction.getValues().size());
			assertEquals("100", partitionFunction.getValues().get(0));
			assertEquals("1000", partitionFunction.getValues().get(1));
			assertEquals("10000", partitionFunction.getValues().get(2));

			PartitionScheme partitionScheme = catalog.getPartitionSchemes()
					.get("METADATA_PARTITION_SCHEME");
			assertNotNull(partitionScheme);
			assertEquals("METADATA_PARTITION_FUNCTION",
					partitionScheme.getPartitionFunctionName());
			assertEquals(5, partitionScheme.getTableSpaces().size());
			assertEquals("PRIMARY",
					partitionScheme.getTableSpaces().get(0).getName());
			assertEquals("PRIMARY",
					partitionScheme.getTableSpaces().get(4).getName());
			assertTrue(catalog.getSettings().size() > 0);
			assertNotNull(catalog.getSettings().get("Collation"));
			assertEquals(catalog.getSettings().get("Collation").getValue(),
					catalog.getCollation());
			assertNotNull(catalog.getTableSpaces().get("PRIMARY"));
			assertTrue(catalog.getTableSpaces().get("PRIMARY")
					.getTableSpaceFiles().size() > 0);

			assertEquals("dbo", catalog.getUsers().get("METADATA_USER")
					.getDefaultSchemaName());
			assertNotNull(catalog.getRoles().get("METADATA_ROLE"));
			assertTrue(catalog.getRoleMembers().stream().anyMatch(member ->
					"METADATA_USER".equals(member.getGranteeName())
					&& "METADATA_ROLE".equals(member.getMemberRoleName())),
					catalog.getRoleMembers().toString());
			assertTrue(catalog.getObjectPrivileges().stream().anyMatch(privilege ->
					"METADATA_PARENT".equals(privilege.getObjectName())
					&& "METADATA_ROLE".equals(privilege.getGranteeName())
					&& "SELECT".equals(privilege.getPrivilege())));
			assertTrue(catalog.getColumnPrivileges().stream().anyMatch(privilege ->
					"METADATA_PARENT".equals(privilege.getObjectName())
					&& "NAME".equals(privilege.getColumnName())
					&& "METADATA_USER".equals(privilege.getGranteeName())
					&& "UPDATE".equals(privilege.getPrivilege())));

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
			assertEquals("PARENT_REGION", foreignKey.getColumns().get(1).getName());
			assertEquals("ID", foreignKey.getRelatedColumns().get(0).getName());
			assertEquals("REGION", foreignKey.getRelatedColumns().get(1).getName());
			Index index = child.getIndexes()
					.get("IDX_METADATA_CHILD_PARENT");
			assertNotNull(index);
			assertEquals("PARENT_ID", index.getColumns().get(0).getName());
			assertEquals(Order.Desc, index.getColumns().get(0).getOrder());
			assertEquals("PARENT_REGION", index.getColumns().get(1).getName());
			assertEquals(Order.Asc, index.getColumns().get(1).getOrder());
			assertTrue(index.getWhere().contains("AMOUNT"), index.getWhere());
			assertNotNull(index.getIncludes().get("CODE"));
			assertEquals("true", index.getSpecifics()
					.get("OPTIMIZE_FOR_SEQUENTIAL_KEY"));
			Index columnStoreIndex = parent.getIndexes()
					.get("IDX_METADATA_PARENT_COLUMNSTORE");
			assertNotNull(columnStoreIndex);
			assertEquals(IndexType.NonClusteredColumnStore,
					columnStoreIndex.getIndexType());
			assertEquals(2, columnStoreIndex.getColumns().size());
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

			Table typeCoverage = schema.getTables().get("METADATA_TYPES");
			assertNotNull(typeCoverage);
			assertNotNull(schema.getXmlSchemas().get("METADATA_XML_SCHEMA"));
			assertTrue(schema.getXmlSchemas().get("METADATA_XML_SCHEMA")
					.getDefinition().toString().contains("root"));
			assertEquals(DataType.NVARCHAR,
					typeCoverage.getColumns().get("UNICODE_VALUE").getDataType());
			assertEquals(123, typeCoverage.getColumns().get("UNICODE_VALUE")
					.getLength().intValue());
			assertEquals(DataType.VARCHAR,
					typeCoverage.getColumns().get("MAX_VALUE").getDataType());
			assertEquals(DataType.TIMESTAMP_WITH_TIMEZONE,
					typeCoverage.getColumns().get("OFFSET_VALUE").getDataType());
			assertEquals(DataType.VARBINARY,
					typeCoverage.getColumns().get("BINARY_VALUE").getDataType());
			assertEquals("dbo.METADATA_XML_SCHEMA",
					typeCoverage.getColumns().get("XML_VALUE")
						.getSpecifics().get("xmlschema"));
			assertEquals("partial(1, \"XXXX\", 1)",
					typeCoverage.getColumns().get("MASKED_VALUE")
						.getMaskingFunction());

			Table temporal = schema.getTables().get("METADATA_TEMPORAL");
			assertNotNull(temporal);
			assertEquals("SYSTEM_VERSIONED_TEMPORAL_TABLE",
					temporal.getSpecifics().get("temporal_type"));
			assertNotNull(temporal.getColumns().get("VALID_FROM"));
			assertNotNull(temporal.getColumns().get("VALID_TO"));

			Sequence sequence = schema.getSequences().get("METADATA_SEQ");
			assertNotNull(sequence);
			assertEquals(50L, sequence.getStartValue().longValue());
			assertEquals(10L, sequence.getIncrementBy().longValue());

			Type tableType = schema.getTypes().get("METADATA_TABLE_TYPE");
			assertNotNull(tableType);
			assertNotNull(tableType.getColumns().get("ITEM_ID"));
			assertTrue(tableType.getColumns().get("ITEM_NAME").isNotNull());

			var view = schema.getViews().get("METADATA_VIEW");
			assertNotNull(view);
			assertNotNull(view.getColumns().get("TOTAL"));
			assertTrue(view.getStatement().toString()
					.contains("METADATA_CHILD"), view.getStatement().toString());

			var procedure = schema.getProcedures()
					.get("METADATA_PROCEDURE");
			assertNotNull(procedure);
			assertEquals(1, procedure.getArguments().size());
			assertEquals("@PARENT_ID", procedure.getArguments().get(0).getName());

			var function = schema.getFunctions().get("METADATA_FUNCTION");
			assertNotNull(function);
			assertEquals(1, function.getArguments().size());
			assertEquals("@AMOUNT", function.getArguments().get(0).getName());

			Trigger trigger = schema.getTriggers().get("METADATA_TRIGGER");
			assertNotNull(trigger);
			assertEquals("METADATA_CHILD", trigger.getTableName());
			assertEquals("AFTER", trigger.getActionTiming());
			assertTrue(trigger.getEventManipulation().contains("INSERT"));
			assertTrue(trigger.getStatement().toString()
					.contains("METADATA_PARENT"), trigger.getStatement().toString());

			var synonym = schema.getSynonyms().get("METADATA_PARENT_SYNONYM");
			assertNotNull(synonym);
			assertTrue(synonym.getObjectName().contains("METADATA_PARENT"),
					synonym.getObjectName());
		}
	}

	private static Connection createTestDatabaseConnection() throws SQLException {
		return DriverManager.getConnection(SQL_SERVER.getJdbcUrl()
				+ ";databaseName=" + DATABASE_NAME, SQL_SERVER.getUsername(),
				SQL_SERVER.getPassword());
	}

	private boolean connectionCatalogEquals(Connection connection, Catalog catalog) {
		try {
			return connection.getCatalog().equalsIgnoreCase(catalog.getName());
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}

	private void createSchemaObjects(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS METADATA_AUX.METADATA_PARENT");
			statement.execute("""
					IF SCHEMA_ID('METADATA_AUX') IS NOT NULL
						DROP SCHEMA METADATA_AUX
					""");
			statement.execute("""
					IF DATABASE_PRINCIPAL_ID('METADATA_ROLE') IS NOT NULL
					AND DATABASE_PRINCIPAL_ID('METADATA_USER') IS NOT NULL
						ALTER ROLE METADATA_ROLE DROP MEMBER METADATA_USER
					""");
			statement.execute("""
					IF DATABASE_PRINCIPAL_ID('METADATA_ROLE') IS NOT NULL
						DROP ROLE METADATA_ROLE
					""");
			statement.execute("""
					IF DATABASE_PRINCIPAL_ID('METADATA_USER') IS NOT NULL
						DROP USER METADATA_USER
					""");
			statement.execute("DROP SYNONYM IF EXISTS METADATA_PARENT_SYNONYM");
			statement.execute("DROP TRIGGER IF EXISTS METADATA_TRIGGER");
			statement.execute("DROP VIEW IF EXISTS METADATA_VIEW");
			statement.execute("DROP PROCEDURE IF EXISTS METADATA_PROCEDURE");
			statement.execute("DROP FUNCTION IF EXISTS METADATA_FUNCTION");
			statement.execute("DROP TABLE IF EXISTS METADATA_CHILD");
			statement.execute("DROP TABLE IF EXISTS METADATA_PARENT");
			statement.execute("DROP TABLE IF EXISTS METADATA_TYPES");
			statement.execute("""
					IF OBJECT_ID('METADATA_TEMPORAL', 'U') IS NOT NULL
						ALTER TABLE METADATA_TEMPORAL SET (SYSTEM_VERSIONING = OFF)
					""");
			statement.execute("DROP TABLE IF EXISTS METADATA_TEMPORAL");
			statement.execute("DROP TABLE IF EXISTS METADATA_TEMPORAL_HISTORY");
			statement.execute("""
					IF EXISTS (SELECT 1 FROM sys.partition_schemes
						WHERE name = 'METADATA_PARTITION_SCHEME')
						DROP PARTITION SCHEME METADATA_PARTITION_SCHEME
					""");
			statement.execute("""
					IF EXISTS (SELECT 1 FROM sys.partition_functions
						WHERE name = 'METADATA_PARTITION_FUNCTION')
						DROP PARTITION FUNCTION METADATA_PARTITION_FUNCTION
					""");
			statement.execute("""
					IF EXISTS (SELECT 1 FROM sys.xml_schema_collections
						WHERE name = 'METADATA_XML_SCHEMA')
						DROP XML SCHEMA COLLECTION dbo.METADATA_XML_SCHEMA
					""");
			statement.execute("DROP TYPE IF EXISTS METADATA_TABLE_TYPE");
			statement.execute("DROP SEQUENCE IF EXISTS METADATA_SEQ");
			statement.execute("""
					CREATE PARTITION FUNCTION METADATA_PARTITION_FUNCTION (BIGINT)
					AS RANGE RIGHT FOR VALUES (100, 1000, 10000)
					""");
			statement.execute("""
					CREATE PARTITION SCHEME METADATA_PARTITION_SCHEME
					AS PARTITION METADATA_PARTITION_FUNCTION
					ALL TO ([PRIMARY])
					""");
			statement.execute("""
					CREATE XML SCHEMA COLLECTION dbo.METADATA_XML_SCHEMA AS N'
					<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
						<xs:element name="root" type="xs:string"/>
					</xs:schema>'
					""");
			statement.execute("""
					CREATE SEQUENCE METADATA_SEQ AS BIGINT
					START WITH 50 INCREMENT BY 10
					""");
			statement.execute("""
					CREATE TABLE METADATA_PARENT (
						ID BIGINT NOT NULL,
						REGION CHAR(2) NOT NULL,
						NAME NVARCHAR(100) NOT NULL,
						CONSTRAINT PK_METADATA_PARENT PRIMARY KEY (ID, REGION)
					)
					""");
			statement.execute("""
					CREATE TABLE METADATA_CHILD (
						ID BIGINT IDENTITY(100, 5) NOT NULL,
						PARENT_ID BIGINT NOT NULL,
						PARENT_REGION CHAR(2) NOT NULL,
						CODE VARCHAR(40) NOT NULL
							CONSTRAINT DF_METADATA_CHILD_CODE DEFAULT 'unknown',
						AMOUNT DECIMAL(18, 2) NOT NULL,
						TOTAL AS (AMOUNT * 2) PERSISTED,
						CONSTRAINT PK_METADATA_CHILD PRIMARY KEY (ID),
						CONSTRAINT UK_METADATA_CHILD_CODE UNIQUE (CODE),
						CONSTRAINT CK_METADATA_CHILD_AMOUNT CHECK (AMOUNT >= 0),
						CONSTRAINT FK_METADATA_CHILD_PARENT
							FOREIGN KEY (PARENT_ID, PARENT_REGION)
							REFERENCES METADATA_PARENT(ID, REGION)
					)
					""");
			statement.execute("""
					CREATE NONCLUSTERED COLUMNSTORE INDEX IDX_METADATA_PARENT_COLUMNSTORE
					ON METADATA_PARENT(NAME, REGION)
					""");
			statement.execute("""
					CREATE INDEX IDX_METADATA_CHILD_PARENT
					ON METADATA_CHILD(PARENT_ID DESC, PARENT_REGION ASC) INCLUDE (CODE)
					WHERE AMOUNT > 0
					WITH (OPTIMIZE_FOR_SEQUENTIAL_KEY = ON)
					""");
			statement.execute("""
					CREATE TABLE METADATA_TYPES (
						ID UNIQUEIDENTIFIER NOT NULL,
						UNICODE_VALUE NVARCHAR(123),
						MAX_VALUE VARCHAR(MAX),
						OFFSET_VALUE DATETIMEOFFSET(7),
						BINARY_VALUE VARBINARY(64),
						XML_VALUE XML(dbo.METADATA_XML_SCHEMA),
						MASKED_VALUE VARCHAR(100)
							MASKED WITH (FUNCTION = 'partial(1,"XXXX",1)')
					)
					""");
			statement.execute("""
					CREATE TABLE METADATA_TEMPORAL (
						ID BIGINT NOT NULL PRIMARY KEY,
						VALUE NVARCHAR(100),
						VALID_FROM DATETIME2 GENERATED ALWAYS AS ROW START NOT NULL,
						VALID_TO DATETIME2 GENERATED ALWAYS AS ROW END NOT NULL,
						PERIOD FOR SYSTEM_TIME (VALID_FROM, VALID_TO)
					) WITH (SYSTEM_VERSIONING = ON
						(HISTORY_TABLE = dbo.METADATA_TEMPORAL_HISTORY))
					""");
			statement.execute("""
					EXEC sys.sp_addextendedproperty
						@name = N'MS_Description',
						@value = N'Metadata parent table',
						@level0type = N'SCHEMA', @level0name = N'dbo',
						@level1type = N'TABLE', @level1name = N'METADATA_PARENT'
					""");
			statement.execute("CREATE USER METADATA_USER WITHOUT LOGIN WITH DEFAULT_SCHEMA = dbo");
			statement.execute("CREATE ROLE METADATA_ROLE AUTHORIZATION dbo");
			statement.execute("ALTER ROLE METADATA_ROLE ADD MEMBER METADATA_USER");
			statement.execute("GRANT SELECT ON dbo.METADATA_PARENT TO METADATA_ROLE");
			statement.execute("GRANT UPDATE (NAME) ON dbo.METADATA_PARENT TO METADATA_USER");
			statement.execute("CREATE SCHEMA METADATA_AUX AUTHORIZATION dbo");
			statement.execute("""
					CREATE TABLE METADATA_AUX.METADATA_PARENT (
						ID INT NOT NULL,
						AUXILIARY_VALUE NVARCHAR(30),
						CONSTRAINT PK_METADATA_AUX_PARENT PRIMARY KEY (ID)
					)
					""");
			statement.execute("""
					CREATE INDEX IDX_METADATA_AUX_PARENT
					ON METADATA_AUX.METADATA_PARENT(AUXILIARY_VALUE)
					""");
			statement.execute("""
					EXEC sys.sp_addextendedproperty
						@name = N'MS_Description', @value = N'Display name',
						@level0type = N'SCHEMA', @level0name = N'dbo',
						@level1type = N'TABLE', @level1name = N'METADATA_PARENT',
						@level2type = N'COLUMN', @level2name = N'NAME'
					""");
			statement.execute("""
					CREATE TYPE METADATA_TABLE_TYPE AS TABLE (
						ITEM_ID BIGINT PRIMARY KEY,
						ITEM_NAME NVARCHAR(100) NOT NULL
					)
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
			statement.execute("""
					CREATE TRIGGER METADATA_TRIGGER ON METADATA_CHILD
					AFTER INSERT
					AS
					BEGIN
						SET NOCOUNT ON;
						SELECT p.ID FROM METADATA_PARENT p
						JOIN inserted i ON i.PARENT_ID = p.ID;
					END
					""");
			statement.execute("""
					CREATE SYNONYM METADATA_PARENT_SYNONYM
					FOR dbo.METADATA_PARENT
					""");
		}
	}
}
