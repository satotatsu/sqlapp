/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.oracle;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.ColumnMigration;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultValueMigration.Difference;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultValueMigration.Outcome;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/**
 * Converts defaults read from Oracle for PostgreSQL, and SQL Server definitions
 * for Oracle, creates the tables and checks the values the defaults produce.
 */
class OracleDefaultMigrationTest {
	private static final OracleContainer ORACLE = ReusableTestcontainers
			.configure(new OracleContainer(OracleTestEnvironment.image()));
	private static final PostgreSQLContainer POSTGRES = ReusableTestcontainers
			.configure(new PostgreSQLContainer("postgres:17"));

	@BeforeAll
	static void start() {
		ReusableTestcontainers.start(ORACLE);
		ReusableTestcontainers.start(POSTGRES);
	}

	@AfterAll
	static void stop() {
		ReusableTestcontainers.stop(ORACLE);
		ReusableTestcontainers.stop(POSTGRES);
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
		try (var statement = connection.createStatement()) {
			for (final var operation : dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE)) {
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
	void oracleToPostgres() throws Exception {
		final Table source;
		final Dialect from;
		try (var connection = ORACLE.createConnection(""); var statement = connection.createStatement()) {
			statement.execute("""
					CREATE TABLE D (
					 ID NUMBER(10) NOT NULL PRIMARY KEY,
					 D1 DATE DEFAULT SYSDATE,
					 D2 DATE DEFAULT CURRENT_DATE,
					 D3 DATE DEFAULT TRUNC(SYSDATE),
					 T1 TIMESTAMP DEFAULT SYSTIMESTAMP,
					 T2 TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
					 T4 TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP,
					 T6 TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP),
					 S VARCHAR2(10) DEFAULT 'a''b',
					 SN NVARCHAR2(10) DEFAULT N'x',
					 N NUMBER(5,2) DEFAULT 1.5,
					 NEG NUMBER DEFAULT -5,
					 B BOOLEAN DEFAULT TRUE,
					 EX NUMBER DEFAULT 1+2
					)""");
			from = DialectResolver.getInstance().getDialect(connection);
			final var reader = from.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName(connection.getMetaData().getUserName());
			reader.setObjectName("D");
			source = reader.getAllFull(connection).getFirst();
		}
		try (var connection = POSTGRES.createConnection("")) {
			final Dialect to = DialectResolver.getInstance().getDialect(connection);
			final Table target = source.clone();
			target.setCatalogName(null);
			target.setSchemaName("public");
			// Tablespaces and the primary key's implicit index are Oracle specific and outside DEFAULT conversion.
			target.getIndexes().clear();
			target.setTableSpace(null);
			final var results = convert(source, from, to, target);
			assertEquals("CAST(statement_timestamp() AS timestamp)", results.get("D1").candidate().getDefaultValue());
			assertTrue(results.get("D1").defaultValue().differences().contains(Difference.TIME_BASIS));
			assertEquals(Outcome.CONVERTED_EXACT, results.get("D2").defaultValue().outcome());
			assertEquals(Outcome.REQUIRES_ACTION, results.get("D3").defaultValue().outcome());
			assertEquals("CAST(statement_timestamp() AS timestamp)", results.get("T1").candidate().getDefaultValue());
			assertEquals(Outcome.CONVERTED_EXACT, results.get("T2").defaultValue().outcome());
			assertEquals("statement_timestamp()", results.get("T4").candidate().getDefaultValue());
			assertEquals(Outcome.CONVERTED_EXACT, results.get("T4").defaultValue().outcome());
			assertEquals("(statement_timestamp() AT TIME ZONE 'UTC')", results.get("T6").candidate().getDefaultValue());
			assertEquals("'x'", results.get("SN").candidate().getDefaultValue());
			assertEquals("TRUE", results.get("B").candidate().getDefaultValue());
			assertEquals(Outcome.REQUIRES_ACTION, results.get("EX").defaultValue().outcome());
			create(connection, to, target);
			try (var statement = connection.createStatement()) {
				statement.executeUpdate("INSERT INTO public.\"D\"(\"ID\") VALUES (1)");
				try (var rows = statement.executeQuery("SELECT d.*, LOCALTIMESTAMP AS local_now, "
						+ "now() AT TIME ZONE 'UTC' AS utc_now FROM public.\"D\" d")) {
					assertTrue(rows.next());
					final LocalDateTime localNow = rows.getObject("local_now", LocalDateTime.class);
					assertClose(localNow, rows.getObject("D2", LocalDateTime.class));
					assertClose(localNow, rows.getObject("T2", LocalDateTime.class));
					assertClose(rows.getObject("utc_now", LocalDateTime.class),
							rows.getObject("T4", OffsetDateTime.class).atZoneSameInstant(ZoneOffset.UTC)
									.toLocalDateTime());
					assertClose(rows.getObject("utc_now", LocalDateTime.class), rows.getObject("T6", LocalDateTime.class));
					assertNull(rows.getObject("D3"));
					assertEquals("a'b", rows.getString("S"));
					assertEquals("x", rows.getString("SN"));
					assertEquals(0, new BigDecimal("1.5").compareTo(rows.getBigDecimal("N")));
					assertEquals(-5, rows.getInt("NEG"));
					assertTrue(rows.getBoolean("B"));
					assertNull(rows.getObject("EX"));
				}
			}
		}
	}

	@Test
	void sqlServerDefinitionsToOracle() throws Exception {
		final Dialect from = DialectResolver.getInstance().getDialect("Microsoft SQL Server", 16, 0, 0);
		final Table source = new Table("S_DEFAULTS");
		source.getColumns().add("ID", c -> c.setDataType(DataType.INT).setNotNull(true));
		source.getColumns().add("LOCAL_TS", c -> c.setDataType(DataType.TIMESTAMP).setLength(3L)
				.setDefaultValue("sysdatetime()"));
		source.getColumns().add("UTC_TS", c -> c.setDataType(DataType.TIMESTAMP).setLength(6L)
				.setDefaultValue("sysutcdatetime()"));
		source.getColumns().add("INSTANT_TS", c -> c.setDataType(DataType.TIMESTAMP_WITH_TIMEZONE).setLength(6L)
				.setDefaultValue("sysdatetimeoffset()"));
		source.getColumns().add("TODAY", c -> c.setDataType(DataType.DATE).setDefaultValue("CONVERT([date],getdate())"));
		source.getColumns().add("LABEL", c -> c.setDataType(DataType.NVARCHAR).setLength(10L).setDefaultValue("N'a''b'"));
		source.getColumns().add("FLAG", c -> c.setDataType(DataType.BOOLEAN).setDefaultValue("1"));
		source.setPrimaryKey("PK_S_DEFAULTS", source.getColumns().get("ID"));
		try (var connection = ORACLE.createConnection("")) {
			final Dialect to = DialectResolver.getInstance().getDialect(connection);
			final Table target = source.clone();
			final var results = convert(source, from, to, target);
			assertEquals("SYSTIMESTAMP", results.get("LOCAL_TS").candidate().getDefaultValue());
			assertEquals("SYS_EXTRACT_UTC(SYSTIMESTAMP)", results.get("UTC_TS").candidate().getDefaultValue());
			assertEquals("TRUNC(SYSDATE)", results.get("TODAY").candidate().getDefaultValue());
			assertEquals("N'a''b'", results.get("LABEL").candidate().getDefaultValue());
			assertEquals("TRUE", results.get("FLAG").candidate().getDefaultValue());
			create(connection, to, target);
			try (var statement = connection.createStatement()) {
				statement.executeUpdate("INSERT INTO S_DEFAULTS(ID) VALUES (1)");
				try (var rows = statement.executeQuery("SELECT s.*, CAST(SYSTIMESTAMP AS TIMESTAMP) AS LOCAL_NOW, "
						+ "SYS_EXTRACT_UTC(SYSTIMESTAMP) AS UTC_NOW, TRUNC(SYSDATE) AS D FROM S_DEFAULTS s")) {
					assertTrue(rows.next());
					assertClose(rows.getObject("LOCAL_NOW", LocalDateTime.class),
							rows.getObject("LOCAL_TS", LocalDateTime.class));
					assertClose(rows.getObject("UTC_NOW", LocalDateTime.class),
							rows.getObject("UTC_TS", LocalDateTime.class));
					assertClose(rows.getObject("UTC_NOW", LocalDateTime.class),
							rows.getObject("INSTANT_TS", OffsetDateTime.class).atZoneSameInstant(ZoneOffset.UTC)
									.toLocalDateTime());
					assertEquals(rows.getObject("D", LocalDate.class), rows.getObject("TODAY", LocalDate.class));
					assertEquals("a'b", rows.getString("LABEL"));
					assertTrue(rows.getBoolean("FLAG"));
				}
			}
		}
	}
}
