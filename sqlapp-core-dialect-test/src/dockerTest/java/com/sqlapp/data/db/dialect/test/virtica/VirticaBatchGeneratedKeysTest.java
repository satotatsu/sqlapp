/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.virtica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;

/** Vertica 25.1 JDBC generated-key behavior probe. */
class VirticaBatchGeneratedKeysTest {
	private static final GenericContainer<?> VERTICA = ReusableTestcontainers.configure(
			new GenericContainer<>(DockerImageName.parse("ratiopbc/vertica-ce:v25.1.0-0"))
					.withExposedPorts(5433)
					.waitingFor(Wait.forLogMessage(".*Vertica is now running.*\\n", 1)
							.withStartupTimeout(Duration.ofMinutes(3))));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(VERTICA);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(VERTICA);
	}

	@Test
	void jdbcGeneratedKeysAreUnsupported() throws Exception {
		String url = "jdbc:vertica://localhost:" + VERTICA.getMappedPort(5433) + "/VMart";
		try (Connection connection = DriverManager.getConnection(url, "dbadmin", "");
				Statement statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS sqlapp_key_probe");
			statement.execute("CREATE TABLE sqlapp_key_probe (id IDENTITY, txt VARCHAR(30))");
			assertThrows(java.sql.SQLFeatureNotSupportedException.class, () -> connection.prepareStatement(
					"INSERT INTO sqlapp_key_probe(txt) VALUES (?)", Statement.RETURN_GENERATED_KEYS));
		}
	}
}
