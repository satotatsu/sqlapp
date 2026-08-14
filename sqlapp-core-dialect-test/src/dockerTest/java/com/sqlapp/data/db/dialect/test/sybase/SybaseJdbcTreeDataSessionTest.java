/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.sybase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/** Sybase ASE 16.0 SP03 identity behavior with the jTDS driver. */
class SybaseJdbcTreeDataSessionTest {
	private static final GenericContainer<?> ASE = ReusableTestcontainers.configure(
			new GenericContainer<>(DockerImageName.parse("blieusong/ase-server:latest"))
					.withCreateContainerCmdModifier(command -> command.withHostName("ase-server")
							.withEntrypoint("/home/sybase/bin/entrypoint.sh")
							.withWorkingDir("/home/sybase"))
					.withExposedPorts(5000)
					.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(4))));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(ASE);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(ASE);
	}

	@Test
	void metadataReaderLoadsIdentityPrimaryKeyIndexAndForeignKey() throws SQLException {
		try (Connection connection = DriverManager.getConnection(
				"jdbc:jtds:sybase://localhost:" + ASE.getMappedPort(5000)
						+ "/master", "sa", "sybase");
				Statement statement = connection.createStatement()) {
			try {
				statement.execute("DROP TABLE metadata_table");
			} catch (SQLException ignored) {
				// The isolated test container may not contain the table yet.
			}
			statement.execute("CREATE TABLE metadata_table (id INT IDENTITY NOT NULL, "
					+ "parent_id INT NULL, code VARCHAR(30) NOT NULL, "
					+ "CONSTRAINT pk_metadata_table PRIMARY KEY (id), "
					+ "CONSTRAINT fk_metadata_table_parent FOREIGN KEY (parent_id) "
					+ "REFERENCES metadata_table(id))");
			statement.execute("CREATE INDEX idx_metadata_table_code ON metadata_table(code)");
			var schema = SchemaUtils.getSchema(connection, "dbo", "metadata_table")
					.orElseThrow();
			var table = schema.getTables().get("metadata_table");
			assertNotNull(table);
			assertTrue(table.getColumns().get("id").isIdentity());
			assertEquals("id", table.getConstraints().getPrimaryKeyConstraint()
					.getColumns().get(0).getName());
			assertNotNull(table.getIndexes().get("idx_metadata_table_code"));
			var foreignKey = table.getConstraints().stream()
					.filter(ForeignKeyConstraint.class::isInstance)
					.map(ForeignKeyConstraint.class::cast)
					.findFirst().orElseThrow();
			assertEquals("parent_id", foreignKey.getColumns().get(0).getName());
			assertEquals("id", foreignKey.getRelatedColumns().get(0).getName());
		}
	}

	@Test
	void generatedIdentityFailsBeforePreparingPerRowStatements() throws SQLException {
		try (Connection connection = DriverManager.getConnection(
				"jdbc:jtds:sybase://localhost:" + ASE.getMappedPort(5000) + "/master", "sa", "sybase")) {
			connection.setAutoCommit(false);
			Table table = new Table("sqlapp_identity_probe");
			table.setDialect(DialectResolver.getInstance().getDialect(connection));
			table.getColumns().add(new Column("id").setDataType(DataType.INT).setIdentity(true));
			table.getColumns().add(new Column("txt").setDataType(DataType.VARCHAR).setLength(30));
			SQLException exception = assertThrows(SQLException.class, () -> {
				try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, table)) {
					session.setTableOperationMode(TableOperationMode.INSERT);
					Row row = session.newRow(table);
					row.put("txt", "row-1");
				}
			});
			assertTrue(exception.getMessage().contains("provide explicit key values"), exception::getMessage);
		}
	}
}
