/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.virtica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;

/** Exercises VerticaCopyStream against Vertica CE. */
class VirticaBulkInsertTest {
	private static final GenericContainer<?> VERTICA =
			ReusableTestcontainers.configure(new GenericContainer<>(
					DockerImageName.parse("ratiopbc/vertica-ce:v25.1.0-0"))
					.withExposedPorts(5433)
					.waitingFor(Wait.forLogMessage(
							".*Vertica is now running.*\\n", 1)
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
	void copiesRowsAndPreservesCsvSemantics() throws Exception {
		try (Connection connection = createConnection();
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE sqlapp_bulk_vertica (id IDENTITY, name VARCHAR(200), nullable_value VARCHAR(20), empty_value VARCHAR(20), amount NUMERIC(12,2))");
			final Table table = new Table("sqlapp_bulk_vertica");
			table.getColumns().add(new Column("id").setDataType(DataType.BIGINT)
					.setIdentity(true));
			table.getColumns().add(new Column("name").setDataType(DataType.VARCHAR));
			table.getColumns().add(new Column("nullable_value").setDataType(DataType.VARCHAR));
			table.getColumns().add(new Column("empty_value").setDataType(DataType.VARCHAR));
			table.getColumns().add(new Column("amount").setDataType(DataType.DECIMAL)
					.setLength(12).setScale(2));
			table.getRows().add(row -> {
				row.put("name", "山田,\"太郎\"\nline");
				row.put("nullable_value", null);
				row.put("empty_value", "");
				row.put("amount", new BigDecimal("123.45"));
			});

			assertEquals(1, BulkInsertResolver.execute(connection, table,
					BulkOption.defaults()));
			try (var resultSet = statement.executeQuery(
					"SELECT id, name, nullable_value, empty_value, amount FROM sqlapp_bulk_vertica")) {
				resultSet.next();
				assertEquals(1L, resultSet.getLong("id"));
				assertEquals("山田,\"太郎\"\nline", resultSet.getString("name"));
				assertNull(resultSet.getString("nullable_value"));
				assertEquals("", resultSet.getString("empty_value"));
				assertEquals(new BigDecimal("123.45"), resultSet.getBigDecimal("amount"));
			}
		}
	}

	private static Connection createConnection() throws Exception {
		return DriverManager.getConnection("jdbc:vertica://localhost:"
				+ VERTICA.getMappedPort(5433) + "/VMart", "dbadmin", "");
	}
}
