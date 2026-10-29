/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.transfer.*;

/** Local generated MDB and embedded HSQL only. */
class MdbCsvTransferIntegrationTest {
	@TempDir
	Path directory;

	@Test
	void japaneseMdbPackageImportsRelationshipsAfterSourceIsRemoved() throws Exception {
		var file = directory.resolve("日本語 元データ.mdb");
		try (var connection = DriverManager
				.getConnection("jdbc:ucanaccess://" + file.toAbsolutePath() + ";newDatabaseVersion=V2000");
				var statement = connection.createStatement()) {
			for (String suffix : List.of("", "2")) {
				int key = suffix.isEmpty() ? 1 : 11;
				statement.execute(
						"CREATE TABLE [親 顧客" + suffix + "] ([番号] LONG NOT NULL PRIMARY KEY, [顧客 名] VARCHAR(40))");
				statement.execute("CREATE TABLE [子 明細" + suffix
						+ "] ([番号] LONG NOT NULL PRIMARY KEY, [親番号] LONG, CONSTRAINT FK_PARENT" + suffix
						+ " FOREIGN KEY ([親番号]) REFERENCES [親 顧客" + suffix + "]([番号]))");
				statement.execute("INSERT INTO [親 顧客" + suffix + "] VALUES (" + key + ", '日本語データ')");
				statement.execute("INSERT INTO [子 明細" + suffix + "] VALUES (" + (key + 1) + ", " + key + ")");
			}
		}
		var original = Files.readAllBytes(file);
		var source = new PlanningJdbc.Spec(MdbSourceFile.jdbcUrl(file), "", MdbSourceFile.DRIVER);
		var target = new PlanningJdbc.Spec("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "");
		var service = new FileTransferDesktopService();
		var discovered = (DataMigrationDraftService.Prepared) service.perform(FileTransferPanel.Action.DISCOVER, null,
				directory, source, new char[0], "", null);
		var job = new TransferFileJobDraftBuilder().save(discovered.schema(),
				discovered.schema().tables().stream().map(table -> table.table()).toList(), 100, true, "__NULL__",
				TransferJobConfiguration.SchemaMode.CREATE_MISSING, TransferJobConfiguration.TemporalPolicy.WARN,
				TransferJobConfiguration.CompatibilityPolicy.WARN, "PUBLIC",
				discovered.directory().resolve("transfer.yaml"));
		var exported = (TransferFilePackageIO.Snapshot) service.perform(FileTransferPanel.Action.EXPORT, job.file(),
				directory, source, new char[0], "", null);
		assertEquals(4, exported.manifest().tables().size());
		assertArrayEquals(original, Files.readAllBytes(file));
		Files.move(file, directory.resolve("source-unavailable.mdb"));
		assertFalse(Files.exists(file));
		var review = (FileTransferDesktopService.Reviewed) service.perform(FileTransferPanel.Action.REVIEW,
				exported.file(), directory, target, new char[0], "", null);
		assertTrue(review.schemaSqlDraft().contains(TransferSchemaChangeChecks.NAME_ADJUSTMENT));
		var outcome = (TransferFileImportService.Outcome) service.perform(FileTransferPanel.Action.IMPORT,
				exported.file(), directory, target, new char[0], "", review);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, outcome.phase());
		assertTrue(Files.readString(outcome.auditFile()).contains(TransferSchemaChangeChecks.NAME_ADJUSTMENT));
		try (var connection = DriverManager.getConnection(target.jdbcUrl(), "SA", "");
				var statement = connection.createStatement();
				var rows = statement.executeQuery(
						"SELECT P.\"顧客 名\" FROM \"親 顧客\" P JOIN \"子 明細\" C ON P.\"番号\"=C.\"親番号\" WHERE C.\"番号\"=2")) {
			assertTrue(rows.next());
			assertEquals("日本語データ", rows.getString(1));
			assertFalse(rows.next());
			for (String suffix : List.of("", "2"))
				try (var keys = connection.getMetaData().getImportedKeys(null, "PUBLIC", "子 明細" + suffix)) {
					assertTrue(keys.next());
					assertEquals("親 顧客" + suffix, keys.getString("PKTABLE_NAME"));
				}
		}
	}
}
