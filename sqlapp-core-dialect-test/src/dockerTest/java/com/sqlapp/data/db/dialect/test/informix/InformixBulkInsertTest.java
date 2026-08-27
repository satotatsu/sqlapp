/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.informix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import com.sqlapp.data.db.dialect.test.BulkMigrationTransactionAssertions;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;

class InformixBulkInsertTest {
	private static final GenericContainer<?> INFORMIX = ReusableTestcontainers.configure(
			new GenericContainer<>(DockerImageName.parse(
					"icr.io/informix/informix-developer-database:14.10.FC9W1DE"))
					.withPrivilegedMode(true).withEnv("LICENSE", "accept")
					.withEnv("STORAGE", "local").withEnv("SIZE", "small").withExposedPorts(9088)
					.waitingFor(Wait.forLogMessage(".*'sysadmin' database built successfully.*\\n", 1)
							.withStartupTimeout(Duration.ofMinutes(3))));

	@BeforeAll static void start() { ReusableTestcontainers.start(INFORMIX); }
	@AfterAll static void stop() { ReusableTestcontainers.stop(INFORMIX); }

	@Test
	void databaseCheckpointRollsBackWithTheChunk() throws Exception {
		final String url = "jdbc:informix-sqli://localhost:" + INFORMIX.getMappedPort(9088)
				+ "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y";
		try (var connection = DriverManager.getConnection(url, "informix", "in4mix");
				var statement = connection.createStatement()) {
			try { statement.execute("DROP TABLE sqlapp_chunk_migration_informix"); }
			catch (java.sql.SQLException ignored) { }
			statement.execute("CREATE TABLE sqlapp_chunk_migration_informix "
					+ "(code VARCHAR(20) PRIMARY KEY, name VARCHAR(100))");
			final Table table = new Table("sqlapp_chunk_migration_informix");
			final Column code = new Column("code").setDataType(DataType.VARCHAR).setLength(20);
			table.getColumns().add(code);
			table.getColumns().add(new Column("name").setDataType(DataType.VARCHAR).setLength(100));
			table.setPrimaryKey("pk_sqlapp_chunk_migration_informix", code);
			BulkMigrationTransactionAssertions.assertDatabaseCheckpointAtomic(connection,
					table, "code", "name", "SELECT COUNT(*) FROM sqlapp_chunk_migration_informix");
		}
	}

	@Test
	void insertsBatchesAndSerialValues() throws Exception {
		final String url = "jdbc:informix-sqli://localhost:" + INFORMIX.getMappedPort(9088)
				+ "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y";
		try (var connection = DriverManager.getConnection(url, "informix", "in4mix");
				var statement = connection.createStatement()) {
			try { statement.execute("DROP TABLE sqlapp_bulk_informix"); }
			catch (java.sql.SQLException ignored) { }
			statement.execute("CREATE TABLE sqlapp_bulk_informix (id SERIAL PRIMARY KEY, "
					+ "txt VARCHAR(200), nullable_value VARCHAR(20), empty_value VARCHAR(20))");
			final Table generated = createTable();
			generated.getRows().add(row -> { row.put("txt", "text\nline");
				row.put("nullable_value", null); row.put("empty_value", ""); });
			assertEquals(1, BulkInsertResolver.execute(connection, generated,
					BulkOption.builder().batchSize(2).build()));
			final Table explicit = createTable();
			explicit.getRows().add(row -> { row.put("id", 42); row.put("txt", "explicit");
				row.put("empty_value", ""); });
			assertEquals(1, BulkInsertResolver.execute(connection, explicit,
					BulkOption.builder().keepIdentity(true).build()));
			try (var rs = statement.executeQuery("SELECT * FROM sqlapp_bulk_informix WHERE id=1")) {
				rs.next(); assertEquals("text\nline", rs.getString("txt"));
				assertNull(rs.getString("nullable_value")); assertEquals("", rs.getString("empty_value"));
			}
		}
	}

	@Test
	void upsertsAndSupportsSingleActionModes() throws Exception {
		final String url = "jdbc:informix-sqli://localhost:" + INFORMIX.getMappedPort(9088)
				+ "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y";
		try (var connection = DriverManager.getConnection(url, "informix", "in4mix");
				var statement = connection.createStatement()) {
			try { statement.execute("DROP TABLE sqlapp_upsert_informix"); }
			catch (java.sql.SQLException ignored) { }
			statement.execute("CREATE TABLE sqlapp_upsert_informix (id INT PRIMARY KEY, txt VARCHAR(200))");
			statement.execute("INSERT INTO sqlapp_upsert_informix VALUES (1, 'old')");

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

			try (var rs = statement.executeQuery("SELECT id, txt FROM sqlapp_upsert_informix ORDER BY id")) {
				rs.next(); assertEquals(1, rs.getInt(1)); assertEquals("update-only", rs.getString(2));
				rs.next(); assertEquals(2, rs.getInt(1)); assertEquals("inserted", rs.getString(2));
				rs.next(); assertEquals(3, rs.getInt(1)); assertEquals("insert-only", rs.getString(2));
			}
		}
	}

	private static Table createUpsertTable() {
		final Table table = new Table("sqlapp_upsert_informix");
		table.getColumns().add(new Column("id").setDataType(DataType.INT));
		table.getColumns().add(new Column("txt").setDataType(DataType.VARCHAR));
		table.setPrimaryKey("pk_sqlapp_upsert_informix", table.getColumns().get("id"));
		return table;
	}

	private static Table createTable() {
		final Table table = new Table("sqlapp_bulk_informix");
		table.getColumns().add(new Column("id").setDataType(DataType.SERIAL).setIdentity(true));
		table.getColumns().add(new Column("txt").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("nullable_value").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("empty_value").setDataType(DataType.VARCHAR));
		return table;
	}
}
