/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfSystemProperty(named = "sqlapp.interval.pg.url", matches = "jdbc:postgresql://127[.]0[.]0[.]1:1543[23]/interval_test")
class TransferIntervalServerTest {
	@TempDir Path directory;
	private String name() { return "i_s7_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
	private DataSource pg() { return new DriverSource(System.getProperty("sqlapp.interval.pg.url"), "postgres"); }
	private JdbcDataSource h2() {
		var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:" + UUID.randomUUID()); source.setUser("sa"); return source;
	}
	private Path job(String table, String sourceSchema, String targetSchema) throws Exception {
		return Files.writeString(directory.resolve("job.yaml"), "formatVersion: 1\nsource: from\ntarget: to\ntables: ["
				+ table + "]\nsourceSchema: " + sourceSchema + "\ntargetSchema: " + targetSchema
				+ "\nschemaMode: CREATE_MISSING\ndataMode: FILE\nfiles: {directory: packages, format: CSV, nullValue: __NULL__}\n");
	}
	private TransferFileImportService.Outcome transfer(Path job, DataSource from, DataSource to, String warning) throws Exception {
		var saved = new TransferFileExportService().export(job, Map.of("from", from));
		var service = new TransferFileImportService();
		var review = service.review(saved.file(), saved.fingerprint(), Map.of("to", to));
		assertTrue(review.schemaPreparation().warnings().stream().anyMatch(text -> text.contains(warning)));
		var outcome = service.execute(review, Map.of("to", to), directory);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, outcome.phase());
		assertFalse(outcome.verification().isEmpty());
		assertTrue(outcome.verification().values().stream().allMatch(value -> value.isMatch()));
		assertTrue(Files.readString(outcome.auditFile()).contains(warning));
		return outcome;
	}
	@Test void postgresCalendarDaysBecomeReviewedFixedTimeInH2() throws Exception {
		var from = pg(); var to = h2(); String schema = name(), table = name();
		try (var source = from.getConnection(); var target = to.getConnection(); var sql = source.createStatement()) {
			sql.execute("CREATE SCHEMA " + schema);
			try {
				sql.execute("CREATE TABLE " + schema + "." + table + "(id INT PRIMARY KEY,c INTERVAL(6))");
				sql.execute("INSERT INTO " + schema + "." + table + " VALUES(1,INTERVAL '1 day -1 hour')");
				transfer(job(table, schema, "PUBLIC"), from, to, "CALENDAR_DAYS_AS_24_HOURS");
				try (var rows = target.createStatement().executeQuery("SELECT \"c\" FROM \"" + table + "\"")) {
					assertTrue(rows.next()); assertEquals("INTERVAL '0 23:00:00' DAY TO SECOND", rows.getString(1));
				}
			} finally { sql.execute("DROP SCHEMA " + schema + " CASCADE"); }
		}
	}
	@Test void directJdbcUsesTheSameReviewedProjectionAndVerification() throws Exception {
		var from = pg(); var to = h2(); String schema = name(), table = name();
		try (var source = from.getConnection(); var target = to.getConnection(); var sql = source.createStatement()) {
			sql.execute("CREATE SCHEMA " + schema);
			try {
				sql.execute("CREATE TABLE " + schema + "." + table + "(id INT PRIMARY KEY,c INTERVAL(6))");
				sql.execute("INSERT INTO " + schema + "." + table + " VALUES(1,INTERVAL '1 day -1 hour')");
				Path definition = job(table, schema, "PUBLIC");
				Files.writeString(definition, Files.readString(definition).replace("dataMode: FILE", "dataMode: JDBC_DIRECT")
						.replaceAll("(?m)^files:.*\\n", ""));
				var service = new TransferJobExecutionService();
				var bindings = Map.of("from", from, "to", to);
				var review = service.review(definition, bindings, directory);
				assertTrue(review.prepared().warnings().stream().anyMatch(text -> text.contains("CALENDAR_DAYS_AS_24_HOURS")));
				var outcome = service.execute(review, bindings);
				assertEquals(TransferJobExecutionService.Phase.COMPLETE, outcome.phase());
				assertTrue(outcome.verificationResult().isMatch());
			} finally { sql.execute("DROP SCHEMA " + schema + " CASCADE"); }
		}
	}
	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.CsvSource({"false,false", "true,false", "false,true", "true,true"})
	void postgresCalendarIntervalUsesReviewedStagingProjection(boolean fullScan, boolean postgresTarget) throws Exception {
		var from = pg(); var embedded = new org.hsqldb.jdbc.JDBCDataSource();
		embedded.setUrl("jdbc:hsqldb:mem:" + UUID.randomUUID()); embedded.setUser("SA");
		DataSource to = postgresTarget ? pg() : embedded;
		String schema = name(), table = name(), targetSchema = postgresTarget ? name() : "PUBLIC";
		try (var source = from.getConnection(); var target = to.getConnection(); var sql = source.createStatement();
				var destination = target.createStatement()) {
			sql.execute("CREATE SCHEMA " + schema);
			if (postgresTarget) {
				sql.execute("CREATE SCHEMA " + targetSchema);
				destination.execute("SET search_path TO " + targetSchema);
			}
			try {
				sql.execute("CREATE TABLE " + schema + "." + table + "(id INT" + (fullScan ? "" : " PRIMARY KEY") + ",c INTERVAL(6))");
				sql.execute("INSERT INTO " + schema + "." + table + " VALUES(1,INTERVAL '1 day -1 hour')");
				if (fullScan) sql.execute("INSERT INTO " + schema + "." + table + " SELECT * FROM " + schema + "." + table);
				destination.execute("CREATE TABLE \"" + table + "\"(\"id\" INT" + (fullScan ? "" : " PRIMARY KEY")
						+ ",\"c\" " + (postgresTarget ? "INTERVAL(6)" : "INTERVAL DAY(9) TO SECOND(6)") + ")");
				destination.execute("INSERT INTO \"" + table + "\" VALUES(99,INTERVAL '1' HOUR)");
				Path definition = job(table, schema, targetSchema);
				Files.writeString(definition, Files.readString(definition).replace("dataMode: FILE", "dataMode: JDBC_DIRECT")
						.replaceAll("(?m)^files:.*\\n", "") + "targetPreparation: STAGED_REPLACE\ninsertMethod: JDBC_BATCH\n");
				var service = new TransferJobExecutionService(); var bindings = Map.of("from", from, "to", to);
				var review = service.review(definition, bindings, directory);
				assertEquals(!postgresTarget, review.prepared().warnings().stream().anyMatch(text -> text.contains("CALENDAR_DAYS_AS_24_HOURS")));
				new TransferJobReportIO().writeReview(review);
				var outcome = service.execute(review, bindings);
				assertEquals(TransferStagedReplacement.State.COMMITTED, outcome.staging().state());
				assertTrue(outcome.verificationResult().isMatch());
				var report = new TransferJobReportIO().writeOutcomeSnapshot(review, outcome);
				assertEquals(!postgresTarget, Files.readString(report.file()).contains("CALENDAR_DAYS_AS_24_HOURS"));
				try (var rows = destination.executeQuery("SELECT COUNT(*) FROM \"" + table + "\" WHERE \"id\"=1")) {
					assertTrue(rows.next()); assertEquals(fullScan ? 2 : 1, rows.getInt(1));
				}
			} finally {
				sql.execute("DROP SCHEMA " + schema + " CASCADE");
				if (postgresTarget) sql.execute("DROP SCHEMA " + targetSchema + " CASCADE");
			}
		}
	}
	@Test void h2NineDigitDeclarationMapsToPostgresSixDigitsWhenValuesFitExactly() throws Exception {
		var from = h2(); var to = pg(); String schema = name(), table = name();
		try (var source = from.getConnection(); var target = to.getConnection(); var sql = target.createStatement()) {
			sql.execute("CREATE SCHEMA " + schema);
			try {
				source.createStatement().execute("CREATE TABLE \"" + table + "\"(ID INT PRIMARY KEY,Y INTERVAL YEAR(9) TO MONTH,D INTERVAL DAY(9) TO SECOND(9))");
				source.createStatement().execute("INSERT INTO \"" + table + "\" VALUES(1,INTERVAL '-1-2' YEAR TO MONTH,INTERVAL '1 01:00:00.123456' DAY TO SECOND(9))");
				transfer(job(table, "PUBLIC", schema), from, to, "INTERVAL_PRECISION_MAPPING");
				try (var rows = sql.executeQuery("SELECT EXTRACT(EPOCH FROM \"D\") FROM " + schema + ".\"" + table + "\"")) {
					assertTrue(rows.next()); assertEquals(new java.math.BigDecimal("90000.123456"), rows.getBigDecimal(1));
				}
			} finally { sql.execute("DROP SCHEMA " + schema + " CASCADE"); }
		}
	}
	@Test
	@EnabledIfSystemProperty(named = "sqlapp.interval.pg.url", matches = "jdbc:postgresql://127[.]0[.]0[.]1:15433/interval_test")
	void oldPostgresRejectsUnrepresentableTextHoursBeforeTargetDdl() throws Exception {
		var from = h2(); var to = pg(); String schema = name(), table = name();
		try (var source = from.getConnection(); var target = to.getConnection(); var sql = target.createStatement()) {
			sql.execute("CREATE SCHEMA " + schema);
			try {
				source.createStatement().execute("CREATE TABLE \"" + table + "\"(ID INT PRIMARY KEY,D INTERVAL DAY(9) TO SECOND(9))");
				source.createStatement().execute("INSERT INTO \"" + table + "\" VALUES(1,INTERVAL '106751991 04:00:54.775807' DAY(9) TO SECOND(9))");
				var saved = new TransferFileExportService().export(job(table, "PUBLIC", schema), Map.of("from", from));
				var failure = assertThrows(IllegalArgumentException.class, () -> new TransferFileImportService()
						.review(saved.file(), saved.fingerprint(), Map.of("to", to)));
				assertTrue(failure.getMessage().contains("INTERVAL_VALUE_NOT_REPRESENTABLE"), failure::getMessage);
				try (var rows = sql.executeQuery("SELECT to_regclass('" + schema + "." + table + "')")) {
					assertTrue(rows.next()); assertNull(rows.getObject(1));
				}
			} finally { sql.execute("DROP SCHEMA " + schema + " CASCADE"); }
		}
	}
	@Test void postgresRejectsExcessNanosecondsBeforeTargetDdl() throws Exception {
		var from = h2(); var to = pg(); String schema = name(), table = name();
		try (var source = from.getConnection(); var target = to.getConnection(); var sql = target.createStatement()) {
			sql.execute("CREATE SCHEMA " + schema);
			try {
				source.createStatement().execute("CREATE TABLE \"" + table + "\"(ID INT PRIMARY KEY,D INTERVAL DAY(9) TO SECOND(9))");
				source.createStatement().execute("INSERT INTO \"" + table + "\" VALUES(1,INTERVAL '0.000000001' SECOND(9,9))");
				var saved = new TransferFileExportService().export(job(table, "PUBLIC", schema), Map.of("from", from));
				var failure = assertThrows(IllegalArgumentException.class, () -> new TransferFileImportService()
						.review(saved.file(), saved.fingerprint(), Map.of("to", to)));
				assertTrue(failure.getMessage().contains("INTERVAL_VALUE_NOT_REPRESENTABLE"), failure::getMessage);
				try (var query = target.prepareStatement("SELECT to_regclass(?)")) {
					query.setString(1, schema + "." + table);
					try (var rows = query.executeQuery()) { assertTrue(rows.next()); assertNull(rows.getObject(1)); }
				}
			} finally { sql.execute("DROP SCHEMA " + schema + " CASCADE"); }
		}
	}
	@Test
	@EnabledIfSystemProperty(named = "sqlapp.interval.oracle.url", matches = "jdbc:oracle:thin:@//127[.]0[.]0[.]1:11521/FREEPDB1")
	void postgresCalendarDaysBecomeAuditedAndVerifiedOracleIntervals() throws Exception {
		var from = pg(); var to = new DriverSource(System.getProperty("sqlapp.interval.oracle.url"), "interval_test");
		String schema = name(), table = name().toUpperCase(Locale.ROOT);
		try (var source = from.getConnection(); var target = to.getConnection(); var sql = source.createStatement()) {
			sql.execute("CREATE SCHEMA " + schema);
			try {
				sql.execute("CREATE TABLE " + schema + ".\"" + table + "\"(id INT PRIMARY KEY,c INTERVAL(6))");
				sql.execute("INSERT INTO " + schema + ".\"" + table + "\" VALUES(1,INTERVAL '1 day -1 hour')");
				transfer(job(table, schema, "INTERVAL_TEST"), from, to, "CALENDAR_DAYS_AS_24_HOURS");
				try (var result = target.createStatement(); var rows = result.executeQuery("SELECT \"c\" FROM \"" + table + "\"")) {
					assertTrue(rows.next());
					var dialect = TransferJobPreparationService.dialect(target);
					assertEquals(com.sqlapp.data.interval.IntervalValue.parse("PT23H"), dialect.getDbDataTypes()
							.getDbTypeStrict(com.sqlapp.data.db.datatype.DataType.INTERVAL_DAY_TO_SECOND)
							.getJdbcTypeHandler().getObject(rows, 1));
				}
			} finally {
				sql.execute("DROP SCHEMA " + schema + " CASCADE");
				try (var drop = target.createStatement()) { drop.execute("DROP TABLE \"" + table + "\" PURGE"); }
				catch (SQLException missing) { if (missing.getErrorCode() != 942) throw missing; }
			}
		}
	}
	private static final class DriverSource implements DataSource {
		private final String url, user;
		DriverSource(String url, String user) {
			if (!url.matches("jdbc:postgresql://127[.]0[.]0[.]1:1543[23]/interval_test")
					&& !url.equals("jdbc:oracle:thin:@//127.0.0.1:11521/FREEPDB1"))
				throw new IllegalArgumentException("Dedicated local interval test database required");
			this.url = url; this.user = user;
		}
		@Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(url, user, "interval_test_2026"); }
		@Override public Connection getConnection(String user, String password) throws SQLException { return DriverManager.getConnection(url, user, password); }
		@Override public java.io.PrintWriter getLogWriter() { return null; }
		@Override public void setLogWriter(java.io.PrintWriter writer) {}
		@Override public void setLoginTimeout(int seconds) {}
		@Override public int getLoginTimeout() { return 0; }
		@Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getLogger("interval-test"); }
		@Override public <T> T unwrap(Class<T> type) throws SQLException { if (type.isInstance(this)) return type.cast(this); throw new SQLException("Not a wrapper"); }
		@Override public boolean isWrapperFor(Class<?> type) { return type.isInstance(this); }
	}
}
