/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.saphana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.saphana.SapHana;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.Table.TableDataStoreType;

/** SAP HANA 2.0 integration coverage for the metadata reader tree. */
class SapHanaMetadataReaderTest {
	private static final String PASSWORD = "HxeTest9xA";
	private static final String PASSWORD_JSON =
			"{\"master_password\":\"" + PASSWORD + "\"}";
	private static final Path PASSWORD_DIRECTORY = createPasswordDirectory();
	private static final GenericContainer<?> HANA = ReusableTestcontainers.configure(
			new GenericContainer<>(DockerImageName.parse(
					"saplabs/hanaexpress:2.00.088.00.20251110.1"))
					.withFileSystemBind(PASSWORD_DIRECTORY.toString(),
							"/hana/mounts", BindMode.READ_WRITE)
					.withCommand("--passwords-url",
							"file:///hana/mounts/password.json",
							"--agree-to-sap-license", "--dont-check-system")
					.withExposedPorts(39041)
					.withCreateContainerCmdModifier(command -> command
							.withHostName("hxehost").getHostConfig()
							.withShmSize(1L << 30))
					.waitingFor(Wait.forLogMessage(".*Startup finished.*", 1)
							.withStartupTimeout(Duration.ofMinutes(15))));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(HANA);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(HANA);
		try {
			Files.deleteIfExists(PASSWORD_DIRECTORY.resolve("password.json"));
			Files.deleteIfExists(PASSWORD_DIRECTORY);
		} catch (Exception e) {
			// HANA normally removes password.json itself; cleanup is best effort.
		}
	}

	@Test
	void testReadsRepresentativeSchemaObjectsFromHana2() throws SQLException {
		try (Connection connection = createConnection();
				Statement statement = connection.createStatement()) {
			try {
				createObjects(statement);
				createSecurityObjects(statement);
				var dialect = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(SapHana.class, dialect);
			var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName("METADATA_TEST");
			var schema = reader.getAllFull(connection).stream()
					.filter(s -> "METADATA_TEST".equals(s.getName()))
					.findFirst().orElseThrow();
			var parent = schema.getTables().get("METADATA_PARENT");
			assertNotNull(parent);
			assertEquals(TableDataStoreType.Column,
					parent.getTableDataStoreType());
			assertEquals("Metadata parent table", parent.getRemarks());
			assertEquals("Display name",
					parent.getColumns().get("NAME").getRemarks());
			var rowStore = schema.getTables().get("METADATA_ROW_STORE");
			assertNotNull(rowStore);
			assertEquals(TableDataStoreType.Row,
					rowStore.getTableDataStoreType());
			var child = schema.getTables().get("METADATA_CHILD");
			assertNotNull(child);
			assertEquals(IdentityGenerationType.ByDefault,
					child.getColumns().get("ID").getIdentityGenerationType());
			assertTrue(child.getColumns().get("CODE").getDefaultValue()
					.contains("unknown"));
			var foreignKey = assertInstanceOf(ForeignKeyConstraint.class,
					child.getConstraints().get("FK_METADATA_CHILD_PARENT"));
			assertEquals(2, foreignKey.getColumns().size());
			var childIndex = child.getIndexes()
					.get("IDX_METADATA_CHILD_PARENT");
			assertNotNull(childIndex);
			assertEquals(IndexType.InvertedValue, childIndex.getIndexType());
			assertNotNull(schema.getSequences().get("METADATA_SEQ"));
			assertNotNull(schema.getViews().get("METADATA_VIEW"));
			var synonym = schema.getSynonyms().get("METADATA_PARENT_SYNONYM");
			assertNotNull(synonym);
			assertEquals("METADATA_TEST", synonym.getSchemaName());
			assertEquals("METADATA_TEST", synonym.getObjectSchemaName());
			assertEquals("METADATA_PARENT", synonym.getObjectName());
			var partitioned = schema.getTables().get("METADATA_PARTITIONED");
			assertNotNull(partitioned);
			assertEquals(PartitioningType.Range,
					partitioned.getPartitioning().getPartitioningType());
			assertEquals("BUCKET", partitioned.getPartitioning()
					.getPartitioningColumns().get(0).getName());
			assertEquals(3, partitioned.getPartitioning().getPartitions().size());
			var multiPartitioned = schema.getTables()
					.get("METADATA_MULTI_PARTITIONED");
			assertNotNull(multiPartitioned);
			assertEquals(PartitioningType.Hash,
					multiPartitioned.getPartitioning().getPartitioningType());
			assertEquals(PartitioningType.Range,
					multiPartitioned.getPartitioning().getSubPartitioningType());
			assertEquals("ID", multiPartitioned.getPartitioning()
					.getPartitioningColumns().get(0).getName());
			assertEquals("BUCKET", multiPartitioned.getPartitioning()
					.getSubPartitioningColumns().get(0).getName());
			assertEquals(2,
					multiPartitioned.getPartitioning().getPartitions().size());
			assertEquals(6, multiPartitioned.getPartitioning().getPartitions()
					.stream().mapToInt(partition -> partition.getSubPartitions().size())
					.sum());
			var documents = schema.getTables().get("METADATA_DOCUMENTS");
			assertNotNull(documents);
			var fulltextIndex = documents.getIndexes()
					.get("IDX_METADATA_DOCUMENTS_TEXT");
			assertNotNull(fulltextIndex);
			assertEquals(IndexType.FullText, fulltextIndex.getIndexType());
			var procedure = schema.getProcedures().get("METADATA_PROCEDURE");
			assertNotNull(procedure);
			assertNotNull(procedure.getStatement());
			assertTrue(procedure.getArguments().size() >= 2);
			var function = schema.getFunctions().get("METADATA_FUNCTION");
			assertNotNull(function);
			assertNotNull(function.getDefinition());
			assertTrue(function.getArguments().stream()
					.anyMatch(argument -> "P_AMOUNT".equals(argument.getName())));
			var trigger = schema.getTriggers().get("METADATA_TRIGGER");
			assertNotNull(trigger);
			assertEquals("AFTER", trigger.getActionTiming());
			assertTrue(trigger.getEventManipulation().contains("INSERT"));
			assertEquals("METADATA_TEST", trigger.getTableSchemaName());
			assertEquals("METADATA_CHILD", trigger.getTableName());
				assertNotNull(trigger.getDefinition());
				var catalogReader = dialect.getCatalogReader();
				var roleReader = catalogReader.getRoleReader();
				roleReader.setObjectName("METADATA_ROLE");
				assertNotNull(roleReader.getAll(connection).stream()
						.filter(role -> "METADATA_ROLE".equals(role.getName()))
						.findFirst().orElseThrow());
				var userReader = catalogReader.getUserReader();
				userReader.setObjectName("METADATA_USER");
				assertNotNull(userReader.getAll(connection).stream()
						.filter(user -> "METADATA_USER".equals(user.getName()))
						.findFirst().orElseThrow());
				var roleMemberReader = catalogReader.getRoleMemberReader();
				roleMemberReader.setGrantee("METADATA_USER");
				assertTrue(roleMemberReader.getAll(connection).stream()
						.anyMatch(member -> "METADATA_ROLE".equals(
								member.getMemberRoleName())
								&& member.isAdmin()));
				var objectPrivilegeReader = catalogReader
						.getObjectPrivilegeReader();
				objectPrivilegeReader.setSchemaName("METADATA_TEST");
				objectPrivilegeReader.setObjectName("METADATA_PARENT");
				assertTrue(objectPrivilegeReader.getAll(connection).stream()
						.anyMatch(privilege -> "METADATA_ROLE".equals(
								privilege.getGranteeName())
								&& "SELECT".equals(privilege.getPrivilege())
								&& privilege.isGrantable()));
			} finally {
				cleanupSecurityObjects(statement);
			}
		}
	}

	private Connection createConnection() throws SQLException {
		String url = "jdbc:sap://localhost:" + HANA.getMappedPort(39041)
				+ "/?databaseName=HXE";
		return DriverManager.getConnection(url, "SYSTEM", PASSWORD);
	}

	private void createObjects(final Statement statement) throws SQLException {
		drop(statement, "DROP SCHEMA METADATA_TEST CASCADE");
		statement.execute("CREATE SCHEMA METADATA_TEST");
		statement.execute("CREATE SEQUENCE METADATA_TEST.METADATA_SEQ START WITH 50 INCREMENT BY 10");
		statement.execute("""
				CREATE COLUMN TABLE METADATA_TEST.METADATA_PARTITIONED (
				 ID BIGINT NOT NULL, BUCKET INTEGER NOT NULL, PAYLOAD NVARCHAR(100))
				 PARTITION BY RANGE (BUCKET) (
				  PARTITION 0 <= VALUES < 100,
				  PARTITION 100 <= VALUES < 200,
				  PARTITION OTHERS)
				""");
		statement.execute("""
				CREATE COLUMN TABLE METADATA_TEST.METADATA_MULTI_PARTITIONED (
				 ID BIGINT NOT NULL, BUCKET INTEGER NOT NULL,
				 PAYLOAD NVARCHAR(100))
				 PARTITION BY HASH (ID) PARTITIONS 2,
				 RANGE (BUCKET) (
				  PARTITION 0 <= VALUES < 100,
				  PARTITION 100 <= VALUES < 200,
				  PARTITION OTHERS)
				""");
		statement.execute("""
				CREATE COLUMN TABLE METADATA_TEST.METADATA_DOCUMENTS (
				 ID BIGINT NOT NULL PRIMARY KEY, CONTENT NCLOB)
				""");
		statement.execute("""
				CREATE FULLTEXT INDEX IDX_METADATA_DOCUMENTS_TEXT
				 ON METADATA_TEST.METADATA_DOCUMENTS(CONTENT)
				 FAST PREPROCESS OFF
				""");
		statement.execute("""
				CREATE COLUMN TABLE METADATA_TEST.METADATA_PARENT (
				 ID BIGINT NOT NULL, REGION NVARCHAR(2) NOT NULL,
				 NAME NVARCHAR(100),
				 CONSTRAINT PK_METADATA_PARENT PRIMARY KEY (ID, REGION))
				""");
		statement.execute("COMMENT ON TABLE METADATA_TEST.METADATA_PARENT IS 'Metadata parent table'");
		statement.execute("COMMENT ON COLUMN METADATA_TEST.METADATA_PARENT.NAME IS 'Display name'");
		statement.execute("""
				CREATE ROW TABLE METADATA_TEST.METADATA_ROW_STORE (
				 ID BIGINT NOT NULL PRIMARY KEY, VALUE NVARCHAR(100))
				""");
		statement.execute("""
				CREATE COLUMN TABLE METADATA_TEST.METADATA_CHILD (
				 ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
				 PARENT_ID BIGINT NOT NULL, PARENT_REGION NVARCHAR(2) NOT NULL,
				 CODE NVARCHAR(40) DEFAULT 'unknown' NOT NULL,
				 AMOUNT DECIMAL(18,2) NOT NULL,
				 CONSTRAINT UK_METADATA_CHILD_CODE UNIQUE (CODE),
				 CONSTRAINT CK_METADATA_CHILD_AMOUNT CHECK (AMOUNT >= 0),
				 CONSTRAINT FK_METADATA_CHILD_PARENT
				 FOREIGN KEY (PARENT_ID, PARENT_REGION)
				 REFERENCES METADATA_TEST.METADATA_PARENT(ID, REGION))
				""");
		statement.execute("""
				CREATE INDEX IDX_METADATA_CHILD_PARENT
				 ON METADATA_TEST.METADATA_CHILD(PARENT_ID, PARENT_REGION)
				""");
		statement.execute("""
				CREATE VIEW METADATA_TEST.METADATA_VIEW AS
				 SELECT ID, CODE, AMOUNT FROM METADATA_TEST.METADATA_CHILD
				""");
		statement.execute("""
				CREATE SYNONYM METADATA_TEST.METADATA_PARENT_SYNONYM
				 FOR METADATA_TEST.METADATA_PARENT
				""");
		statement.execute("""
				CREATE PROCEDURE METADATA_TEST.METADATA_PROCEDURE (
				 IN P_ID BIGINT, OUT P_NAME NVARCHAR(100))
				 LANGUAGE SQLSCRIPT AS
				 BEGIN
				   SELECT NAME INTO P_NAME FROM METADATA_TEST.METADATA_PARENT
				    WHERE ID = P_ID;
				 END
				""");
		statement.execute("""
				CREATE FUNCTION METADATA_TEST.METADATA_FUNCTION (
				 IN P_AMOUNT DECIMAL(18,2))
				 RETURNS RESULT DECIMAL(18,2)
				 LANGUAGE SQLSCRIPT AS
				 BEGIN
				   RESULT := :P_AMOUNT * 2;
				 END
				""");
		statement.execute("""
				CREATE COLUMN TABLE METADATA_TEST.METADATA_AUDIT (
				 CHILD_ID BIGINT NOT NULL)
				""");
		statement.execute("""
				CREATE TRIGGER METADATA_TEST.METADATA_TRIGGER
				 AFTER INSERT ON METADATA_TEST.METADATA_CHILD
				 REFERENCING NEW ROW NEW_ROW
				 FOR EACH ROW
				 BEGIN
				   INSERT INTO METADATA_TEST.METADATA_AUDIT
				    VALUES (:NEW_ROW.ID);
				 END
				""");
	}

	private void drop(final Statement statement, final String sql) {
		try {
			statement.execute(sql);
		} catch (SQLException e) {
			// The disposable test database may not contain the schema yet.
		}
	}

	private void createSecurityObjects(final Statement statement)
			throws SQLException {
		drop(statement, "DROP USER METADATA_USER CASCADE");
		drop(statement, "DROP ROLE METADATA_ROLE");
		statement.execute("CREATE USER METADATA_USER PASSWORD HxeTest9xB NO FORCE_FIRST_PASSWORD_CHANGE");
		statement.execute("CREATE ROLE METADATA_ROLE");
		statement.execute("GRANT METADATA_ROLE TO METADATA_USER WITH ADMIN OPTION");
		statement.execute("GRANT SELECT ON METADATA_TEST.METADATA_PARENT TO METADATA_ROLE WITH GRANT OPTION");
	}

	private void cleanupSecurityObjects(final Statement statement) {
		drop(statement, "DROP USER METADATA_USER CASCADE");
		drop(statement, "DROP ROLE METADATA_ROLE");
	}

	private static Path createPasswordDirectory() {
		try {
			Path directory = Files.createTempDirectory("sqlapp-hana-metadata-");
			Files.writeString(directory.resolve("password.json"), PASSWORD_JSON,
					StandardCharsets.UTF_8);
			return directory;
		} catch (Exception e) {
			throw new ExceptionInInitializerError(e);
		}
	}
}
