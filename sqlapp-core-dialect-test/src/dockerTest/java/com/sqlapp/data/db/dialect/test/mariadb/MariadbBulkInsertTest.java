/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.mariadb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mariadb.MariaDBContainer;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;

/** Exercises MariaDB Connector/J LOAD DATA LOCAL INFILE against MariaDB 11.8. */
class MariadbBulkInsertTest {
	private static final MariaDBContainer MARIADB = ReusableTestcontainers
			.configure(new MariaDBContainer("mariadb:11.8")
					.withCommand("--local-infile=1"));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(MARIADB);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(MARIADB);
	}

	@Test
	void loadsRowsAndPreservesDelimitedValues() throws Exception {
		try (Connection connection = MARIADB.createConnection("?allowLocalInfile=true");
				var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS sqlapp_bulk_mariadb");
			statement.execute("CREATE TABLE sqlapp_bulk_mariadb ("
					+ "id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(200), "
					+ "nullable_value VARCHAR(20), empty_value VARCHAR(20), "
					+ "payload VARBINARY(20))");
			final Table table = new Table("sqlapp_bulk_mariadb");
			table.getColumns().add(new Column("id").setDataType(DataType.BIGINT)
					.setIdentity(true));
			table.getColumns().add(new Column("name").setDataType(DataType.VARCHAR));
			table.getColumns().add(new Column("nullable_value").setDataType(DataType.VARCHAR));
			table.getColumns().add(new Column("empty_value").setDataType(DataType.VARCHAR));
			table.getColumns().add(new Column("payload").setDataType(DataType.VARBINARY));
			table.getRows().add(row -> {
				row.put("name", "山田\nline\\path");
				row.put("nullable_value", null);
				row.put("empty_value", "");
				row.put("payload", new byte[] { 0, (byte) 0xff });
			});

			assertEquals(1, BulkInsertResolver.execute(connection, table,
					BulkOption.defaults()));
			try (var resultSet = statement.executeQuery(
					"SELECT id, name, nullable_value, empty_value, payload "
							+ "FROM sqlapp_bulk_mariadb")) {
				resultSet.next();
				assertEquals(1L, resultSet.getLong("id"));
				assertEquals("山田\nline\\path", resultSet.getString("name"));
				assertNull(resultSet.getString("nullable_value"));
				assertEquals("", resultSet.getString("empty_value"));
				assertArrayEquals(new byte[] { 0, (byte) 0xff }, resultSet.getBytes("payload"));
			}
		}
	}
}
