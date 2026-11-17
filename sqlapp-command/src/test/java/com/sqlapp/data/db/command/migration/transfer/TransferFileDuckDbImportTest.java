/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.zaxxer.hikari.HikariDataSource;

/**
 * A CSV package imported into DuckDB is verified under DuckDB's snapshot
 * isolation. Embedded HSQLDB and DuckDB; no external database is used.
 */
class TransferFileDuckDbImportTest {
	@TempDir
	Path directory;

	@Test
	void importsAndVerifiesIntoDuckDb() throws Exception {
		final JDBCDataSource source = new JDBCDataSource();
		source.setUrl("jdbc:hsqldb:mem:duckdb_import_" + UUID.randomUUID());
		source.setUser("SA");
		source.setPassword("");
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute(
					"CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(40)," + " AMOUNT DECIMAL(12,2))");
			statement.execute("INSERT INTO ITEMS VALUES (1, 'one', 1.50), (2, NULL, NULL), (3, '三', 300.00)");
		}
		final Path job = Files.writeString(directory.resolve("transfer.yaml"), """
				formatVersion: 1
				source: from
				target: to
				tables: [ITEMS]
				schemaMode: CREATE_MISSING
				dataMode: FILE
				files: {directory: packages, format: CSV}
				""");
		final var exported = new TransferFileExportService().export(job, Map.of("from", source));
		try (HikariDataSource target = new HikariDataSource()) {
			target.setJdbcUrl("jdbc:duckdb:" + directory.resolve("work.duckdb").toString().replace('\\', '/'));
			final var service = new TransferFileImportService();
			final var review = service.review(exported.file(), exported.fingerprint(), Map.of("to", target));
			final var outcome = service.execute(review, Map.of("to", target), directory);

			assertEquals(TransferJobExecutionService.Phase.COMPLETE, outcome.phase());
			assertEquals(1, outcome.verification().size());
			assertTrue(outcome.verification().values().stream().allMatch(result -> result.isMatch()));
			try (var connection = target.getConnection();
					var statement = connection.createStatement();
					var rows = statement.executeQuery("SELECT count(*), sum(AMOUNT) FROM ITEMS")) {
				rows.next();
				assertEquals(3, rows.getInt(1));
				assertEquals(new java.math.BigDecimal("301.50"), rows.getBigDecimal(2));
			}
		}
	}
}
