/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.sqlapp.data.db.dialect.ColumnMigration;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpression;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultValueMigration.Difference;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultValueMigration.Outcome;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.EvaluationPoint;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TimeBasis;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/**
 * Reads SQL Server defaults with sqlapp, converts them for PostgreSQL, creates
 * the table on PostgreSQL and checks the values the defaults produce.
 */
class SqlServerToPostgresDefaultMigrationTest {
	private static final String DATABASE = "DEFAULT_MIGRATION_TEST";
	private static final MSSQLServerContainer SQL_SERVER = ReusableTestcontainers
			.configure(new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense());
	private static final PostgreSQLContainer POSTGRES = ReusableTestcontainers
			.configure(new PostgreSQLContainer("postgres:17"));

	@BeforeAll
	static void start() throws SQLException {
		ReusableTestcontainers.start(SQL_SERVER);
		ReusableTestcontainers.start(POSTGRES);
		try (Connection connection = SQL_SERVER.createConnection(""); var statement = connection.createStatement()) {
			statement.execute("CREATE DATABASE " + DATABASE);
		}
	}

	@AfterAll
	static void stop() {
		ReusableTestcontainers.stop(SQL_SERVER);
		ReusableTestcontainers.stop(POSTGRES);
	}

	@Test
	void convertsSqlServerDefaultsForPostgres() throws Exception {
		final Table source;
		final Dialect sourceDialect;
		try (Connection connection = DriverManager.getConnection(SQL_SERVER.getJdbcUrl() + ";databaseName=" + DATABASE,
				SQL_SERVER.getUsername(), SQL_SERVER.getPassword()); var statement = connection.createStatement()) {
			statement.execute("""
					CREATE TABLE dbo.defaults (
						id int NOT NULL PRIMARY KEY,
						local_ts datetime2(7) DEFAULT SYSDATETIME(),
						legacy_ts datetime DEFAULT CURRENT_TIMESTAMP,
						utc_ts datetime2(6) DEFAULT SYSUTCDATETIME(),
						instant_ts datetimeoffset(6) DEFAULT SYSDATETIMEOFFSET(),
						today date DEFAULT CAST(GETDATE() AS date),
						flag bit DEFAULT 1,
						label nvarchar(10) DEFAULT N'a''b',
						amount decimal(5,2) DEFAULT 1.50,
						negative int DEFAULT -5,
						null_text varchar(10) DEFAULT 'NULL',
						computed int DEFAULT 1+2,
						guid uniqueidentifier DEFAULT newid()
					)
					""");
			sourceDialect = DialectResolver.getInstance().getDialect(connection);
			final var reader = sourceDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setCatalogName(connection.getCatalog());
			reader.setSchemaName("dbo");
			reader.setObjectName("defaults");
			source = reader.getAllFull(connection).getFirst();
		}
		try (Connection connection = POSTGRES.createConnection("")) {
			final Dialect targetDialect = DialectResolver.getInstance().getDialect(connection);
			final Map<String, ColumnMigration.Result> results = new LinkedHashMap<>();
			final Table target = source.clone();
			target.setCatalogName(null);
			target.setSchemaName("public");
			target.setName("defaults");
			for (final Column column : source.getColumns()) {
				final var result = ColumnMigration.convert(column, sourceDialect, targetDialect);
				results.put(column.getName(), result);
				target.getColumns().get(column.getName()).setDefaultValue(result.candidate().getDefaultValue());
			}
			assertConversions(results);

			final var registry = targetDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			try (var statement = connection.createStatement()) {
				for (final var operation : registry.createSql(target, SqlType.CREATE)) {
					try {
						statement.execute(operation.getSqlText());
					} catch (final SQLException e) {
						throw new AssertionError(operation.getSqlText(), e);
					}
				}
			}
			assertInsertedValues(connection);
			assertStatementStartEvaluation(connection);
			assertReadBackSemantics(connection, targetDialect);
		}
	}

	private static void assertConversions(final Map<String, ColumnMigration.Result> results) {
		final var local = results.get("local_ts").defaultValue();
		assertEquals("CAST(statement_timestamp() AS timestamp)", local.targetExpression());
		assertEquals(Outcome.CONVERTED_APPROXIMATE, local.outcome());
		assertEquals(List.of(Difference.TIME_BASIS, Difference.PRECISION), local.differences());
		assertEquals("CAST(statement_timestamp() AS timestamp)",
				results.get("legacy_ts").defaultValue().targetExpression());
		assertEquals(List.of(Difference.TIME_BASIS), results.get("legacy_ts").defaultValue().differences());
		final var utc = results.get("utc_ts").defaultValue();
		assertEquals("(statement_timestamp() AT TIME ZONE 'UTC')", utc.targetExpression());
		assertEquals(Outcome.CONVERTED_EXACT, utc.outcome());
		final var instant = results.get("instant_ts").defaultValue();
		assertEquals("statement_timestamp()", instant.targetExpression());
		assertEquals(Outcome.CONVERTED_EXACT, instant.outcome());
		assertEquals("CAST(statement_timestamp() AS date)", results.get("today").defaultValue().targetExpression());
		assertEquals("TRUE", results.get("flag").candidate().getDefaultValue());
		assertEquals("'a''b'", results.get("label").candidate().getDefaultValue());
		assertEquals("1.50", results.get("amount").candidate().getDefaultValue());
		assertEquals("-5", results.get("negative").candidate().getDefaultValue());
		assertEquals("'NULL'", results.get("null_text").candidate().getDefaultValue());
		for (final String name : List.of("computed", "guid")) {
			final var result = results.get(name);
			assertEquals(Outcome.REQUIRES_ACTION, result.defaultValue().outcome(), name);
			assertNull(result.candidate().getDefaultValue(), name);
			assertFalse(result.blockingDiagnostics().isEmpty(), name);
		}
		for (final String name : List.of("id", "local_ts", "legacy_ts", "utc_ts", "instant_ts", "today", "flag",
				"label", "amount", "negative", "null_text")) {
			results.get(name).requireComplete();
		}
	}

	private static void assertInsertedValues(final Connection connection) throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.execute("SET TIME ZONE 'Asia/Tokyo'");
			statement.executeUpdate("INSERT INTO public.defaults(id) VALUES (1)");
			try (var rows = statement.executeQuery(
					"""
							SELECT d.*, now() AT TIME ZONE 'UTC' AS utc_now, LOCALTIMESTAMP AS local_now, CURRENT_DATE AS today_now
							FROM public.defaults d WHERE id = 1""")) {
				assertTrue(rows.next());
				final LocalDateTime localNow = rows.getObject("local_now", LocalDateTime.class);
				final LocalDateTime utcNow = rows.getObject("utc_now", LocalDateTime.class);
				assertClose(localNow, rows.getObject("local_ts", LocalDateTime.class));
				assertClose(localNow, rows.getObject("legacy_ts", LocalDateTime.class));
				assertClose(utcNow, rows.getObject("utc_ts", LocalDateTime.class));
				final OffsetDateTime instant = rows.getObject("instant_ts", OffsetDateTime.class);
				assertTrue(Duration
						.between(utcNow, instant.toInstant().atOffset(java.time.ZoneOffset.UTC).toLocalDateTime()).abs()
						.toSeconds() < 5);
				assertEquals(rows.getObject("today_now", LocalDate.class), rows.getObject("today", LocalDate.class));
				assertTrue(rows.getBoolean("flag"));
				assertEquals("a'b", rows.getString("label"));
				assertEquals(new BigDecimal("1.50"), rows.getBigDecimal("amount"));
				assertEquals(-5, rows.getInt("negative"));
				assertEquals("NULL", rows.getString("null_text"));
				assertNull(rows.getObject("computed"));
				assertNull(rows.getObject("guid"));
			}
		}
	}

	/** statement_timestamp() differs between two statements of one transaction. */
	private static void assertStatementStartEvaluation(final Connection connection) throws SQLException {
		connection.setAutoCommit(false);
		try (var statement = connection.createStatement()) {
			statement.executeUpdate("INSERT INTO public.defaults(id) VALUES (2)");
			statement.execute("SELECT pg_sleep(0.05)");
			statement.executeUpdate("INSERT INTO public.defaults(id) VALUES (3)");
			try (var rows = statement.executeQuery(
					"SELECT (SELECT instant_ts FROM public.defaults WHERE id = 3) - (SELECT instant_ts FROM public.defaults WHERE id = 2) > interval '40 milliseconds'")) {
				assertTrue(rows.next());
				assertTrue(rows.getBoolean(1));
			}
			connection.commit();
		} finally {
			connection.setAutoCommit(true);
		}
	}

	/**
	 * The defaults read back from PostgreSQL are recognized with the semantics that
	 * were selected.
	 */
	private static void assertReadBackSemantics(final Connection connection, final Dialect dialect) throws Exception {
		final var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName("public");
		reader.setObjectName("defaults");
		final Table table = reader.getAllFull(connection).getFirst();
		final var support = dialect.getDefaultExpressionSupport();
		final TemporalSemantics local = assertInstanceOf(DefaultExpression.CurrentTime.class,
				support.parse(table.getColumns().get("local_ts").getDefaultValue(), table.getColumns().get("local_ts")))
				.semantics();
		assertEquals(EvaluationPoint.STATEMENT_START, local.point());
		assertEquals(TimeBasis.SESSION_TIME_ZONE, local.basis());
		final TemporalSemantics utc = assertInstanceOf(DefaultExpression.CurrentTime.class,
				support.parse(table.getColumns().get("utc_ts").getDefaultValue(), table.getColumns().get("utc_ts")))
				.semantics();
		assertEquals(TimeBasis.UTC, utc.basis());
		assertEquals(new DefaultExpression.BooleanLiteral(true),
				support.parse(table.getColumns().get("flag").getDefaultValue(), table.getColumns().get("flag")));
		assertEquals(new DefaultExpression.StringLiteral("a'b"),
				support.parse(table.getColumns().get("label").getDefaultValue(), table.getColumns().get("label")));
	}

	private static void assertClose(final LocalDateTime expected, final LocalDateTime actual) {
		assertTrue(Duration.between(expected, actual).abs().toSeconds() < 5, expected + " vs " + actual);
	}
}
