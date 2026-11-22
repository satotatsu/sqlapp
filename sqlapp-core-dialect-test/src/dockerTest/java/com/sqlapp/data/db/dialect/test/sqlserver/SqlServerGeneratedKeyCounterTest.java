/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.sqlserver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.sql.DriverManager;
import java.sql.Connection;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.test.GeneratedKeyCounterAssertions;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.GeneratedKeyCounters;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

/** Reseeds SQL Server identities, including a table that never held rows. */
class SqlServerGeneratedKeyCounterTest {
	private static final MSSQLServerContainer SQL_SERVER = ReusableTestcontainers
			.configure(new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense());

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(SQL_SERVER);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(SQL_SERVER);
	}

	private static Connection connection() throws Exception {
		return DriverManager.getConnection(SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(),
				SQL_SERVER.getPassword());
	}

	@Test
	void reseedsAPopulatedIdentity() throws Exception {
		try (var connection = connection(); var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS dbo.key_counter_t");
			statement.execute("CREATE TABLE dbo.key_counter_t(id BIGINT IDENTITY(1,1) PRIMARY KEY, v INT)");
			GeneratedKeyCounterAssertions.assertRaisesPastImportedKeys(connection,
					new Table("key_counter_t").setSchemaName("dbo"), new Column("id").setIdentity(true), false,
					"SET IDENTITY_INSERT dbo.key_counter_t ON; "
							+ "INSERT INTO dbo.key_counter_t(id, v) VALUES (5, 1), (42, 2); "
							+ "SET IDENTITY_INSERT dbo.key_counter_t OFF",
					"INSERT INTO dbo.key_counter_t(v) VALUES (3)", "SELECT id FROM dbo.key_counter_t WHERE v = 3");
		}
	}

	@Test
	void reseedsAnIdentityThatNeverHeldRows() throws Exception {
		try (var connection = connection(); var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS dbo.key_counter_empty");
			statement.execute("CREATE TABLE dbo.key_counter_empty(id BIGINT IDENTITY(1,1) PRIMARY KEY, v INT)");
			var result = GeneratedKeyCounters.synchronize(connection,
					new Table("key_counter_empty").setSchemaName("dbo"), new Column("id").setIdentity(true), 10L,
					false);
			assertEquals(BigInteger.valueOf(10), result.next(), result.toString());
			statement.execute("INSERT INTO dbo.key_counter_empty(v) VALUES (1)");
			try (var rows = statement.executeQuery("SELECT id FROM dbo.key_counter_empty")) {
				rows.next();
				assertEquals(10L, rows.getLong(1));
			}
		}
	}
}
