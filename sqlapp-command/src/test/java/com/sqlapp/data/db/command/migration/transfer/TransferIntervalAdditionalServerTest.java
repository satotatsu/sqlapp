/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Opt-in fixtures only; add the owning dialect and driver to the test runtime.
 */
@EnabledIfSystemProperty(named = "sqlapp.interval.additional", matches = "true")
class TransferIntervalAdditionalServerTest {
	@TempDir
	Path directory;

	@ParameterizedTest
	@CsvSource({ "informix,CSV,false,false", "informix,TSV,false,false", "informix,JSONL,false,false",
			"vertica,CSV,false,false", "vertica,TSV,false,false", "vertica,JSONL,false,false", "vertica,CSV,true,false",
			"vertica,CSV,true,true" })
	void intervalArraysRoundTripThroughFilesAndNativeSchema(String product, String format, boolean allNull,
			boolean nativeOnly) throws Exception {
		boolean informix = product.equals("informix");
		String table = "i_file_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		var source = new JdbcDataSource();
		source.setURL("jdbc:h2:mem:" + UUID.randomUUID());
		source.setUser("sa");
		DataSource target = new FixtureSource(informix);
		try (var from = source.getConnection();
				var to = target.getConnection();
				var sql = from.createStatement();
				var destination = to.createStatement()) {
			sql.execute("CREATE TABLE \"" + table + "\"(ID INT PRIMARY KEY,C INTERVAL DAY(9) TO SECOND(5) ARRAY)");
			sql.execute(allNull ? "INSERT INTO \"" + table + "\" VALUES(1,NULL)"
					: "INSERT INTO \"" + table
							+ "\" VALUES(1,ARRAY[INTERVAL '999999 01:02:03.12345' DAY(9) TO SECOND(5),INTERVAL '-0 01:00:00' DAY TO SECOND]),(2,ARRAY[]),(3,NULL)");
			Path job = directory.resolve("job.yaml");
			Files.writeString(job,
					"formatVersion: 1\nsource: from\ntarget: to\ntables: [" + table
							+ "]\nsourceSchema: PUBLIC\ntargetSchema: " + (informix ? "informix" : "public")
							+ "\nschemaMode: CREATE_MISSING\ndataMode: FILE\nfiles: {directory: packages, format: "
							+ format + (format.equals("JSONL") ? "" : ", nullValue: __NULL__") + "}\n"
							+ (nativeOnly ? "insertMethod: NATIVE\n" : ""));
			try {
				var saved = new TransferFileExportService().export(job, Map.of("from", source));
				var service = new TransferFileImportService();

				var review = service.review(saved.file(), saved.fingerprint(), Map.of("to", target));
				var result = service.execute(review, Map.of("to", target), directory);
				assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
				assertFalse(result.verification().isEmpty());
				assertTrue(result.verification().values().stream().allMatch(value -> value.isMatch()));
			} finally {
				// Only the random task-owned table in the dedicated fixture database.
				try (var tables = to.getMetaData().getTables(to.getCatalog(), informix ? "informix" : "public", table,
						new String[] { "TABLE" })) {
					if (tables.next())
						destination.execute("DROP TABLE \"" + table + "\"");
				}
			}
		}
	}

	private record FixtureSource(boolean informix) implements DataSource {
		@Override
		public Connection getConnection() throws SQLException {
			return DriverManager.getConnection(informix
					? "jdbc:informix-sqli://127.0.0.1:19088/interval_test:INFORMIXSERVER=informix;DELIMIDENT=y;"
					: "jdbc:vertica://127.0.0.1:15433/VMart", informix ? "informix" : "dbadmin",
					informix ? "i_test26" : "");
		}

		@Override
		public Connection getConnection(String user, String password) throws SQLException {
			throw new SQLFeatureNotSupportedException();
		}

		@Override
		public java.io.PrintWriter getLogWriter() {
			return null;
		}

		@Override
		public void setLogWriter(java.io.PrintWriter value) {
		}

		@Override
		public void setLoginTimeout(int value) {
		}

		@Override
		public int getLoginTimeout() {
			return 0;
		}

		@Override
		public java.util.logging.Logger getParentLogger() {
			return java.util.logging.Logger.getGlobal();
		}

		@Override
		public boolean isWrapperFor(Class<?> type) {
			return type.isInstance(this);
		}

		@Override
		public <T> T unwrap(Class<T> type) throws SQLException {
			if (type.isInstance(this))
				return type.cast(this);
			throw new SQLException("Not a wrapper");
		}
	}
}
