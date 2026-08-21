/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.sybase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
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
				statement.execute("DROP PROCEDURE metadata_procedure");
			} catch (SQLException ignored) {
				// The isolated test container may not contain the procedure yet.
			}
			try {
				statement.execute("DROP VIEW metadata_view");
			} catch (SQLException ignored) {
				// The isolated test container may not contain the view yet.
			}
			try {
				statement.execute("DROP TABLE metadata_table");
			} catch (SQLException ignored) {
				// The isolated test container may not contain the table yet.
			}
			statement.execute("CREATE TABLE metadata_table (id INT IDENTITY NOT NULL, "
					+ "parent_id INT NULL, code VARCHAR(30) DEFAULT 'unknown' NOT NULL, "
					+ "normalized_code COMPUTE upper(code), "
					+ "CONSTRAINT pk_metadata_table PRIMARY KEY (id), "
					+ "CONSTRAINT uk_metadata_table_code UNIQUE (code), "
					+ "CONSTRAINT ck_metadata_table_code CHECK (code <> ''), "
					+ "CONSTRAINT fk_metadata_table_parent FOREIGN KEY (parent_id) "
					+ "REFERENCES metadata_table(id))");
			statement.execute("CREATE INDEX idx_metadata_table_code ON metadata_table(code)");
			statement.execute("CREATE INDEX idx_metadata_table_code_desc ON metadata_table(code DESC)");
			statement.execute("CREATE VIEW metadata_view AS SELECT id, code FROM metadata_table");
			statement.execute("CREATE PROCEDURE metadata_procedure @p_id INT AS "
					+ "SELECT code FROM metadata_table WHERE id = @p_id");
			var schema = SchemaUtils.getSchema(connection, "dbo", "metadata_table", "metadata_view",
					"metadata_procedure")
					.orElseThrow();
			var table = schema.getTables().get("metadata_table");
			assertNotNull(table);
			assertEquals(Boolean.TRUE,
					table.getSpecifics().get("has_clustered_index", Boolean.class));
			assertTrue(table.getColumns().get("id").isIdentity());
			assertTrue(table.getColumns().get("code").getDefaultValue()
					.contains("unknown"));
			assertTrue(table.getColumns().get("normalized_code").getFormula()
					.toLowerCase().contains("upper"));
			assertEquals("id", table.getConstraints().getPrimaryKeyConstraint()
					.getColumns().get(0).getName());
			var unique = assertInstanceOf(UniqueConstraint.class,
					table.getConstraints().get("uk_metadata_table_code"));
			assertEquals("code", unique.getColumns().get(0).getName());
			assertTrue(table.getConstraints().stream()
					.anyMatch(CheckConstraint.class::isInstance));
			assertNotNull(table.getIndexes().get("idx_metadata_table_code"));
			assertEquals(Order.Desc, table.getIndexes().get("idx_metadata_table_code_desc")
					.getColumns().get(0).getOrder());
			var foreignKey = table.getConstraints().stream()
					.filter(ForeignKeyConstraint.class::isInstance)
					.map(ForeignKeyConstraint.class::cast)
					.findFirst().orElseThrow();
			assertEquals("parent_id", foreignKey.getColumns().get(0).getName());
			assertEquals("id", foreignKey.getRelatedColumns().get(0).getName());
			assertNotNull(schema.getViews().get("metadata_view"));
			var procedure = schema.getProcedures().get("metadata_procedure");
			assertNotNull(procedure);
			assertTrue(procedure.getDefinition() != null || procedure.getStatement() != null);
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
