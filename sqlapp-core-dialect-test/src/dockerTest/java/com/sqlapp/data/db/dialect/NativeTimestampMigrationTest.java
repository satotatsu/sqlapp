package com.sqlapp.data.db.dialect;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.Locale;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

class NativeTimestampMigrationTest {
	@ParameterizedTest
	@CsvSource({
		"Oracle, 19, 6, TIMESTAMP(6) WITH TIME ZONE",
		"Microsoft SQL Server, 15, 6, DATETIMEOFFSET(6)",
		"H2, 2, 6, TIMESTAMP(6) WITH TIME ZONE",
		"HSQL Database Engine, 2, 6, TIMESTAMP(6) WITH TIME ZONE",
		"Firebird, 4, 4, TIMESTAMP WITH TIME ZONE",
		"DuckDB, 1, 6, TIMESTAMPTZ",
		"Vertica, 12, 6, TIMESTAMP(6) WITH TIME ZONE"
	})
	void preservesNativeInstantType(String product, int major, int precision, String expected) {
		var target = DialectResolver.getInstance().getDialect(product, major, 0, 0);
		var source = source(precision);
		var column = ColumnMigration.convert(source, target).requireComplete();
		assertEquals(DataType.TIMESTAMPVERSION, source.getDataType());
		assertEquals(DataType.TIMESTAMP_WITH_TIMEZONE, column.getDataType());
		assertEquals((long) precision, column.getLength());
		assertEquals(expected, target.createSqlBuilder().addTypeDefinition(column).toString()
				.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim());
		var review = ColumnMigration.convert(source.setOnUpdate("CURRENT_TIMESTAMP"), target);
		assertTrue(review.issues().getFirst().contains("ON UPDATE"));
		assertThrows(IllegalStateException.class, review::requireComplete);
	}

	@ParameterizedTest
	@CsvSource({"Oracle,19,SYSTIMESTAMP", "Microsoft SQL Server,15,SYSDATETIMEOFFSET()"})
	void translatesCurrentTimeDefault(String product, int major, String expression) {
		var target = DialectResolver.getInstance().getDialect(product, major, 0, 0);
		var candidate = ColumnMigration.convert(source(6).setDefaultValue("CURRENT_TIMESTAMP(6)"), target)
				.requireComplete();
		assertEquals(expression, candidate.getDefaultValue());
	}

	@ParameterizedTest
	@CsvSource({"Firebird,4,6", "Firebird,3,4", "DuckDB,1,3", "SQLite,3,6", "Microsoft SQL Server,9,6"})
	void reportsMissingCapabilities(String product, int major, int precision) {
		var target = DialectResolver.getInstance().getDialect(product, major, 0, 0);
		assertThrows(IllegalStateException.class,
				ColumnMigration.convert(source(precision), target)::requireComplete);
	}

	@ParameterizedTest
	@CsvSource({"HSQL Database Engine,2,jdbc:hsqldb:mem:timestamp_migration", "DuckDB,1,jdbc:duckdb:"})
	void executesMigratedTypeAndPreservesTheInstant(String product, int major, String url) throws Exception {
		var target = DialectResolver.getInstance().getDialect(product, major, 0, 0);
		var column = ColumnMigration.convert(source(6), target).requireComplete();
		var type = target.createSqlBuilder().addTypeDefinition(column).toString();
		try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE timestamp_migration_test(ts " + type + ")");
			statement.executeUpdate("INSERT INTO timestamp_migration_test VALUES "
					+ "(CAST('2026-11-19 12:00:00.123456+09:00' AS TIMESTAMP WITH TIME ZONE))");
			try (var rows = statement.executeQuery("SELECT COUNT(*) FROM timestamp_migration_test "
					+ "WHERE ts = CAST('2026-11-19 03:00:00.123456+00:00' AS TIMESTAMP WITH TIME ZONE)")) {
				assertTrue(rows.next());
				assertEquals(1, rows.getInt(1));
			}
		}
	}

	private static Column source(int precision) {
		return new Column("ts").setDataType(DataType.TIMESTAMPVERSION).setDataTypeName("TIMESTAMP")
				.setLength(precision);
	}
}

