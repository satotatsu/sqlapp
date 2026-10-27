/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.transfer.*;

class FileTransferDesktopServiceTest {
	@TempDir
	Path directory;

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void guiAdapterCreatesSharedJobExportsAndImportsWithOnlyTargetConnection(boolean createMissing) throws Exception {
		final var source = new PlanningJdbc.Spec("jdbc:hsqldb:mem:csv_gui_source_" + UUID.randomUUID(), "SA", "");
		final var target = new PlanningJdbc.Spec("jdbc:hsqldb:mem:csv_gui_target_" + UUID.randomUUID(), "SA", "");
		try (var from = DriverManager.getConnection(source.jdbcUrl(), "SA", "");
				var to = DriverManager.getConnection(target.jdbcUrl(), "SA", "")) {
			try (var sql = from.createStatement()) {
				sql.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(40))");
				sql.execute("INSERT INTO ITEMS VALUES(1, 'one'),(2, NULL)");
				sql.execute("CREATE TABLE LOBS(ID INTEGER NOT NULL PRIMARY KEY, CONTENT BLOB)");
			}
			if (!createMissing)
				try (var sql = to.createStatement()) {
					sql.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(40))");
				}
			final var service = new FileTransferDesktopService();
			final var prepared = (DataMigrationDraftService.Prepared) service.perform(FileTransferPanel.Action.DISCOVER,
					null, directory, source, new char[0], "", null);
			assertEquals(1, prepared.schema().tables().stream().filter(choice -> choice.selectable()).count());
			final String table = prepared.schema().tables().stream().filter(choice -> choice.selectable()).findFirst()
					.orElseThrow().table();
			final var job = new TransferFileJobDraftBuilder().save(prepared.schema(), List.of(table), 1, true,
					"__NULL__",
					createMissing ? TransferJobConfiguration.SchemaMode.CREATE_MISSING
							: TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING,
					prepared.directory().resolve("transfer.yaml"));
			assertFalse(job.text().contains(source.jdbcUrl()));
			final var exported = (TransferFilePackageIO.Snapshot) service.perform(FileTransferPanel.Action.EXPORT,
					job.file(), null, source, new char[0], "", null);
			final var review = (FileTransferDesktopService.Reviewed) service.perform(FileTransferPanel.Action.REVIEW,
					exported.file(), null, target, new char[0], "", null);
			final var result = (TransferFileImportService.Outcome) service.perform(FileTransferPanel.Action.IMPORT,
					exported.file(), directory, target, new char[0], "", review);
			assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
			assertTrue(result.verificationRequested());
			assertFalse(result.verification().isEmpty());
			assertEquals(createMissing, result.schemaPreparation().completedStatements() > 0);
			assertTrue(Files.isRegularFile(result.auditFile()));
			try (var sql = to.createStatement();
					var rows = sql.executeQuery("SELECT ID, NAME FROM ITEMS ORDER BY ID")) {
				assertTrue(rows.next());
				assertEquals(1, rows.getInt(1));
				assertEquals("one", rows.getString(2));
				assertTrue(rows.next());
				assertEquals(2, rows.getInt(1));
				assertNull(rows.getString(2));
				assertFalse(rows.next());
			}
		}
	}

	@Test
	void invalidFileCannotExposeSecretsAndPasswordBufferIsCleared() {
		final char[] password = "private-value".toCharArray();
		final var failure = assertThrows(IllegalArgumentException.class,
				() -> new FileTransferDesktopService().perform(FileTransferPanel.Action.EXPORT,
						directory.resolve("missing.yaml"), null,
						new PlanningJdbc.Spec("jdbc:missing:private-value", "user", ""), password, "", null));
		assertArrayEquals(new char[password.length], password);
		assertFalse(failure.getMessage().contains("private-value"));
		assertNull(failure.getCause());
	}
}
