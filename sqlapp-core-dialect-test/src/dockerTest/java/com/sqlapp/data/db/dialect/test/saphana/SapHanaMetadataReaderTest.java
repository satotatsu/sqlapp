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
import com.sqlapp.data.schemas.PartitioningType;

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
			createObjects(statement);
			var dialect = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(SapHana.class, dialect);
			var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName("METADATA_TEST");
			var schema = reader.getAllFull(connection).stream()
					.filter(s -> "METADATA_TEST".equals(s.getName()))
					.findFirst().orElseThrow();
			var parent = schema.getTables().get("METADATA_PARENT");
			assertNotNull(parent);
			var child = schema.getTables().get("METADATA_CHILD");
			assertNotNull(child);
			var foreignKey = assertInstanceOf(ForeignKeyConstraint.class,
					child.getConstraints().get("FK_METADATA_CHILD_PARENT"));
			assertEquals(2, foreignKey.getColumns().size());
			assertNotNull(child.getIndexes().get("IDX_METADATA_CHILD_PARENT"));
			assertNotNull(schema.getSequences().get("METADATA_SEQ"));
			assertNotNull(schema.getViews().get("METADATA_VIEW"));
			var partitioned = schema.getTables().get("METADATA_PARTITIONED");
			assertNotNull(partitioned);
			assertEquals(PartitioningType.Range,
					partitioned.getPartitioning().getPartitioningType());
			assertEquals("BUCKET", partitioned.getPartitioning()
					.getPartitioningColumns().get(0).getName());
			assertEquals(3, partitioned.getPartitioning().getPartitions().size());
			var procedure = schema.getProcedures().get("METADATA_PROCEDURE");
			assertNotNull(procedure);
			assertNotNull(procedure.getStatement());
			assertTrue(procedure.getArguments().size() >= 2);
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
				CREATE COLUMN TABLE METADATA_TEST.METADATA_PARENT (
				 ID BIGINT NOT NULL, REGION NVARCHAR(2) NOT NULL,
				 NAME NVARCHAR(100),
				 CONSTRAINT PK_METADATA_PARENT PRIMARY KEY (ID, REGION))
				""");
		statement.execute("""
				CREATE COLUMN TABLE METADATA_TEST.METADATA_CHILD (
				 ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
				 PARENT_ID BIGINT NOT NULL, PARENT_REGION NVARCHAR(2) NOT NULL,
				 CODE NVARCHAR(40) NOT NULL, AMOUNT DECIMAL(18,2) NOT NULL,
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
				CREATE PROCEDURE METADATA_TEST.METADATA_PROCEDURE (
				 IN P_ID BIGINT, OUT P_NAME NVARCHAR(100))
				 LANGUAGE SQLSCRIPT AS
				 BEGIN
				   SELECT NAME INTO P_NAME FROM METADATA_TEST.METADATA_PARENT
				    WHERE ID = P_ID;
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
