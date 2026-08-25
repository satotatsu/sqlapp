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
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;

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

	private static Table createTable() {
		final Table table = new Table("sqlapp_bulk_informix");
		table.getColumns().add(new Column("id").setDataType(DataType.SERIAL).setIdentity(true));
		table.getColumns().add(new Column("txt").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("nullable_value").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("empty_value").setDataType(DataType.VARCHAR));
		return table;
	}
}
