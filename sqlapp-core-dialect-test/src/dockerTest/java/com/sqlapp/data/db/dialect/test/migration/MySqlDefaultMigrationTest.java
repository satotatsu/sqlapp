/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.migration;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.ColumnMigration;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultValueMigration.Outcome;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/**
 * Converts defaults read from MySQL and MariaDB, and SQL Server definitions,
 * creates the tables on another product and checks the values the defaults
 * produce.
 */
class MySqlDefaultMigrationTest {
	private static final MySQLContainer MYSQL = ReusableTestcontainers.configure(new MySQLContainer("mysql:8.4"));
	private static final MariaDBContainer MARIADB = ReusableTestcontainers
			.configure(new MariaDBContainer("mariadb:11.8"));
	private static final PostgreSQLContainer POSTGRES = ReusableTestcontainers
			.configure(new PostgreSQLContainer("postgres:17"));

	private static final String SOURCE = """
			CREATE TABLE d (
			 id int NOT NULL PRIMARY KEY,
			 dt0 datetime DEFAULT CURRENT_TIMESTAMP,
			 dt3 datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
			 ts6 timestamp(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
			 dd date DEFAULT (CURRENT_DATE),
			 du datetime DEFAULT (UTC_TIMESTAMP),
			 tm time DEFAULT (CURRENT_TIME),
			 s varchar(10) DEFAULT 'a''b',
			 n decimal(5,2) DEFAULT 1.50,
			 neg int DEFAULT -5,
			 b bit(1) DEFAULT b'1',
			 bo boolean DEFAULT true,
			 ex int DEFAULT (1+2)
			)""";

	@BeforeAll
	static void start() {
		ReusableTestcontainers.start(MYSQL);
		ReusableTestcontainers.start(MARIADB);
		ReusableTestcontainers.start(POSTGRES);
	}

	@AfterAll
	static void stop() {
		ReusableTestcontainers.stop(MYSQL);
		ReusableTestcontainers.stop(MARIADB);
		ReusableTestcontainers.stop(POSTGRES);
	}

	private static Table read(final Connection connection, final String ddl, final String name) throws Exception {
		try (var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS " + name);
			statement.execute(ddl);
		}
		final var reader = DialectResolver.getInstance().getDialect(connection).getCatalogReader().getSchemaReader()
				.getTableReader();
		reader.setSchemaName(connection.getCatalog());
		reader.setObjectName(name);
		return reader.getAllFull(connection).getFirst();
	}

	private static Map<String, ColumnMigration.Result> convert(final Table source, final Dialect from,
			final Dialect to, final Table target) {
		final Map<String, ColumnMigration.Result> results = new LinkedHashMap<>();
		for (final Column column : source.getColumns()) {
			final var result = ColumnMigration.convert(column, from, to);
			results.put(column.getName(), result);
			target.getColumns().remove(column.getName());
			target.getColumns().add(result.candidate().clone());
		}
		return results;
	}

	private static void create(final Connection connection, final Dialect dialect, final Table table)
			throws SQLException {
		final var registry = dialect.createSqlFactoryRegistry();
		try (var statement = connection.createStatement()) {
			for (final var operation : registry.createSql(table, SqlType.CREATE)) {
				try {
					statement.execute(operation.getSqlText());
				} catch (final SQLException e) {
					throw new AssertionError(operation.getSqlText(), e);
				}
			}
		}
	}

	private static void assertClose(final LocalDateTime expected, final LocalDateTime actual) {
		assertTrue(Duration.between(expected, actual).abs().toSeconds() < 5, expected + " vs " + actual);
	}

	@Test
	void mysqlToPostgres() throws Exception {
		final Table source;
		final Dialect from;
		try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(),
				MYSQL.getPassword())) {
			from = DialectResolver.getInstance().getDialect(connection);
			source = read(connection, SOURCE, "d");
		}
		assertEquals("b'1'", source.getColumns().get("b").getDefaultValue());
		try (var connection = POSTGRES.createConnection("")) {
			final Dialect to = DialectResolver.getInstance().getDialect(connection);
			final Table target = source.clone();
			target.setCatalogName(null);
			target.setSchemaName("public");
			final var results = convert(source, from, to, target);
			assertEquals("CAST(statement_timestamp() AS timestamp)", results.get("dt0").candidate().getDefaultValue());
			assertEquals(Outcome.CONVERTED_EXACT, results.get("dt0").defaultValue().outcome());
			assertEquals("statement_timestamp()", results.get("ts6").candidate().getDefaultValue(),
					results.get("ts6").issues().toString());
			assertEquals(DataType.TIMESTAMP_WITH_TIMEZONE, results.get("ts6").candidate().getDataType());
			assertEquals(Outcome.CONVERTED_EXACT, results.get("ts6").defaultValue().outcome());
			assertEquals("CAST(statement_timestamp() AS date)", results.get("dd").candidate().getDefaultValue());
			assertEquals("(statement_timestamp() AT TIME ZONE 'UTC')", results.get("du").candidate().getDefaultValue());
			assertEquals("CAST(statement_timestamp() AS time)", results.get("tm").candidate().getDefaultValue());
			assertEquals("B'1'", results.get("b").candidate().getDefaultValue());
			assertEquals("TRUE", results.get("bo").candidate().getDefaultValue());
			assertEquals(Outcome.REQUIRES_ACTION, results.get("ex").defaultValue().outcome());
			create(connection, to, target);
			try (var statement = connection.createStatement()) {
				statement.executeUpdate("INSERT INTO public.d(id) VALUES (1)");
				try (var rows = statement.executeQuery("SELECT d.*, LOCALTIMESTAMP AS local_now, "
						+ "now() AT TIME ZONE 'UTC' AS utc_now, CURRENT_DATE AS today FROM public.d d")) {
					assertTrue(rows.next());
					final LocalDateTime localNow = rows.getObject("local_now", LocalDateTime.class);
					assertClose(localNow, rows.getObject("dt0", LocalDateTime.class));
					assertClose(localNow, rows.getObject("dt3", LocalDateTime.class));
					assertClose(rows.getObject("utc_now", LocalDateTime.class),
							rows.getObject("ts6", OffsetDateTime.class).atZoneSameInstant(java.time.ZoneOffset.UTC)
									.toLocalDateTime());
					assertClose(rows.getObject("utc_now", LocalDateTime.class),
							rows.getObject("du", LocalDateTime.class));
					assertEquals(rows.getObject("today", LocalDate.class), rows.getObject("dd", LocalDate.class));
					assertNotNull(rows.getObject("tm", LocalTime.class));
					assertEquals("a'b", rows.getString("s"));
					assertEquals(new BigDecimal("1.50"), rows.getBigDecimal("n"));
					assertEquals(-5, rows.getInt("neg"));
					assertTrue(rows.getBoolean("b"));
					assertTrue(rows.getBoolean("bo"));
					assertNull(rows.getObject("ex"));
				}
			}
		}
	}

	@Test
	void sqlServerDefinitionsToMysql() throws Exception {
		final Dialect from = DialectResolver.getInstance().getDialect("Microsoft SQL Server", 16, 0, 0);
		final Table source = new Table("s");
		source.getColumns().add("id", c -> c.setDataType(DataType.INT).setNotNull(true));
		source.getColumns().add("local_ts", c -> c.setDataType(DataType.TIMESTAMP).setLength(3L)
				.setDefaultValue("sysdatetime()"));
		source.getColumns().add("utc_ts", c -> c.setDataType(DataType.TIMESTAMP).setLength(6L)
				.setDefaultValue("sysutcdatetime()"));
		source.getColumns().add("today", c -> c.setDataType(DataType.DATE).setDefaultValue("CONVERT([date],getdate())"));
		source.getColumns().add("label", c -> c.setDataType(DataType.NVARCHAR).setLength(10L).setDefaultValue("N'a''b'"));
		source.getColumns().add("flag", c -> c.setDataType(DataType.BOOLEAN).setDefaultValue("1"));
		source.setPrimaryKey("pk_s", source.getColumns().get("id"));
		try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(),
				MYSQL.getPassword())) {
			final Dialect to = DialectResolver.getInstance().getDialect(connection);
			final Table target = source.clone();
			target.setSchemaName(null);
			final var results = convert(source, from, to, target);
			assertEquals("CURRENT_TIMESTAMP(3)", results.get("local_ts").candidate().getDefaultValue());
			assertEquals("(UTC_TIMESTAMP(6))", results.get("utc_ts").candidate().getDefaultValue());
			assertEquals("(CURRENT_DATE)", results.get("today").candidate().getDefaultValue());
			try (var statement = connection.createStatement()) {
				statement.execute("DROP TABLE IF EXISTS s");
			}
			create(connection, to, target);
			try (var statement = connection.createStatement()) {
				statement.executeUpdate("INSERT INTO s(id) VALUES (1)");
				try (var rows = statement.executeQuery(
						"SELECT s.*, NOW(3) AS local_now, UTC_TIMESTAMP(6) AS utc_now, CURRENT_DATE AS d FROM s")) {
					assertTrue(rows.next());
					assertClose(rows.getObject("local_now", LocalDateTime.class),
							rows.getObject("local_ts", LocalDateTime.class));
					assertClose(rows.getObject("utc_now", LocalDateTime.class),
							rows.getObject("utc_ts", LocalDateTime.class));
					assertEquals(rows.getObject("d", LocalDate.class), rows.getObject("today", LocalDate.class));
					assertEquals("a'b", rows.getString("label"));
					assertTrue(rows.getBoolean("flag"));
				}
			}
		}
	}

	@Test
	void mariadbToSqlite() throws Exception {
		final Table source;
		final Dialect from;
		try (var connection = DriverManager.getConnection(MARIADB.getJdbcUrl(), MARIADB.getUsername(),
				MARIADB.getPassword())) {
			from = DialectResolver.getInstance().getDialect(connection);
			source = read(connection, SOURCE.replace(" ts6 timestamp(6) NULL DEFAULT CURRENT_TIMESTAMP(6),\n", ""),
					"d");
		}
		assertEquals("b'1'", source.getColumns().get("b").getDefaultValue());
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			final Dialect to = DialectResolver.getInstance().getDialect(connection);
			final Table target = source.clone();
			target.setCatalogName(null);
			target.setSchemaName(null);
			final var results = convert(source, from, to, target);
			// Collations are product specific and outside DEFAULT conversion.
			target.setCollation(null).setCharacterSet(null);
			target.getColumns().forEach(column -> column.setCollation(null).setCharacterSet(null));
			assertEquals("'1'", results.get("b").candidate().getDefaultValue());
			assertEquals("(datetime('now','localtime'))", results.get("dt0").candidate().getDefaultValue());
			assertEquals("(strftime('%Y-%m-%d %H:%M:%f','now','localtime'))",
					results.get("dt3").candidate().getDefaultValue());
			assertEquals("CURRENT_TIMESTAMP", results.get("du").candidate().getDefaultValue());
			create(connection, to, target);
			try (var statement = connection.createStatement()) {
				statement.executeUpdate("INSERT INTO d(id) VALUES (1)");
				try (var rows = statement.executeQuery("SELECT * FROM d")) {
					assertTrue(rows.next());
					assertTrue(rows.getString("dt0").matches("\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d"),
							rows.getString("dt0"));
					assertTrue(rows.getString("dt3").matches("\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3}"),
							rows.getString("dt3"));
					assertTrue(rows.getString("dd").matches("\\d{4}-\\d\\d-\\d\\d"), rows.getString("dd"));
					assertEquals("a'b", rows.getString("s"));
					assertEquals(-5, rows.getInt("neg"));
				}
			}
		}
	}
}
