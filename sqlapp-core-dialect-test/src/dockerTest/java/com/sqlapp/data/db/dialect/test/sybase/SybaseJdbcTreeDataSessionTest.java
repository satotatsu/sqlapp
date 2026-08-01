/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.sybase;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
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
import com.sqlapp.data.schemas.Row;
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
			assertTrue(exception.getMessage().contains("provide explicit identity values"), exception::getMessage);
		}
	}
}
