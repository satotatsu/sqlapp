/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.sybase;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;

/** Exercises streaming JDBC batches against Sybase ASE 16.0 SP03. */
class SybaseBulkInsertTest {
	private static final GenericContainer<?> ASE = ReusableTestcontainers.configure(
			new GenericContainer<>(DockerImageName.parse("blieusong/ase-server:latest"))
					.withCreateContainerCmdModifier(command -> command.withHostName("ase-server")
							.withEntrypoint("/home/sybase/bin/entrypoint.sh")
							.withWorkingDir("/home/sybase"))
					.withExposedPorts(5000)
					.waitingFor(Wait.forListeningPort()
							.withStartupTimeout(Duration.ofMinutes(4))));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(ASE);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(ASE);
	}

	@Test
	void insertsBatchesAndOmitsIdentity() throws Exception {
		try (Connection connection = createConnection();
				var statement = connection.createStatement()) {
			recreateTable(statement);
			final Table table = createTable();
			for (int i = 0; i < 3; i++) {
				final int index = i;
				table.getRows().add(row -> {
					row.put("txt", "row-" + index + "\npath\\value");
					row.put("nullable_value", null);
					row.put("empty_value", "");
					row.put("payload", new byte[] { 0, (byte) (0xfd + index) });
				});
			}

			assertEquals(3, BulkInsertResolver.execute(connection, table,
					BulkOption.builder().batchSize(2).build()));
			try (var resultSet = statement.executeQuery("SELECT id, txt, nullable_value, "
					+ "empty_value, payload FROM sqlapp_bulk_sybase ORDER BY id")) {
				for (int i = 0; i < 3; i++) {
					resultSet.next();
					assertEquals(i + 1, resultSet.getInt("id"));
					assertEquals("row-" + i + "\npath\\value", resultSet.getString("txt"));
					assertNull(resultSet.getString("nullable_value"));
					assertEquals("", resultSet.getString("empty_value"));
					assertArrayEquals(new byte[] { 0, (byte) (0xfd + i) },
							resultSet.getBytes("payload"));
				}
			}
		}
	}

	@Test
	void preservesExplicitIdentityWhenRequested() throws Exception {
		try (Connection connection = createConnection();
				var statement = connection.createStatement()) {
			recreateTable(statement);
			final Table table = createTable();
			table.getRows().add(row -> {
				row.put("id", 42);
				row.put("txt", "explicit");
				row.put("empty_value", "");
				row.put("payload", new byte[] { 1 });
			});
			assertEquals(1, BulkInsertResolver.execute(connection, table,
					BulkOption.builder().keepIdentity(true).build()));
			try (var resultSet = statement.executeQuery(
					"SELECT id FROM sqlapp_bulk_sybase")) {
				resultSet.next();
				assertEquals(42, resultSet.getInt(1));
			}
		}
	}

	@Test
	void upsertsAndSupportsSingleActionModes() throws Exception {
		try (Connection connection = createConnection(); var statement = connection.createStatement()) {
			try { statement.execute("DROP TABLE sqlapp_upsert_sybase"); }
			catch (java.sql.SQLException ignored) { }
			statement.execute("CREATE TABLE sqlapp_upsert_sybase (id INT PRIMARY KEY, txt VARCHAR(100) NULL)");
			statement.execute("INSERT INTO sqlapp_upsert_sybase VALUES (1, 'old')");

			Table table = createUpsertTable();
			table.getRows().add(row -> { row.put("id", 1); row.put("txt", "updated"); });
			table.getRows().add(row -> { row.put("id", 2); row.put("txt", "inserted"); });
			assertEquals(2, BulkUpsertResolver.execute(connection, table, BulkUpsertOption.defaults()));

			table = createUpsertTable();
			table.getRows().add(row -> { row.put("id", 1); row.put("txt", "update-only"); });
			table.getRows().add(row -> { row.put("id", 3); row.put("txt", "ignored"); });
			BulkUpsertResolver.execute(connection, table,
					BulkUpsertOption.builder().insertWhenNotMatched(false).build());

			table = createUpsertTable();
			table.getRows().add(row -> { row.put("id", 1); row.put("txt", "ignored"); });
			table.getRows().add(row -> { row.put("id", 3); row.put("txt", "insert-only"); });
			BulkUpsertResolver.execute(connection, table,
					BulkUpsertOption.builder().updateWhenMatched(false).build());

			try (var rs = statement.executeQuery("SELECT id, txt FROM sqlapp_upsert_sybase ORDER BY id")) {
				rs.next(); assertEquals(1, rs.getInt(1)); assertEquals("update-only", rs.getString(2));
				rs.next(); assertEquals(2, rs.getInt(1)); assertEquals("inserted", rs.getString(2));
				rs.next(); assertEquals(3, rs.getInt(1)); assertEquals("insert-only", rs.getString(2));
			}
		}
	}

	private static Connection createConnection() throws Exception {
		return DriverManager.getConnection("jdbc:jtds:sybase://localhost:"
				+ ASE.getMappedPort(5000) + "/master", "sa", "sybase");
	}

	private static void recreateTable(final java.sql.Statement statement)
			throws Exception {
		try {
			statement.execute("DROP TABLE sqlapp_bulk_sybase");
		} catch (java.sql.SQLException ignored) {
			// The isolated test database can start without the table.
		}
		statement.execute("CREATE TABLE sqlapp_bulk_sybase (id INT IDENTITY NOT NULL, "
				+ "txt VARCHAR(100), nullable_value VARCHAR(20) NULL, "
				+ "empty_value VARCHAR(20) NULL, payload VARBINARY(20) NULL)");
	}

	private static Table createTable() {
		final Table table = new Table("sqlapp_bulk_sybase");
		table.getColumns().add(new Column("id").setDataType(DataType.INT).setIdentity(true));
		table.getColumns().add(new Column("txt").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("nullable_value").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("empty_value").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("payload").setDataType(DataType.VARBINARY));
		return table;
	}

	private static Table createUpsertTable() {
		final Table table = new Table("sqlapp_upsert_sybase");
		table.getColumns().add(new Column("id").setDataType(DataType.INT));
		table.getColumns().add(new Column("txt").setDataType(DataType.VARCHAR));
		table.setPrimaryKey("pk_sqlapp_upsert_sybase", table.getColumns().get("id"));
		return table;
	}
}
