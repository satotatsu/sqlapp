/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.transfer.*;

class FileTransferDesktopServiceTest {
	@TempDir
	Path directory;

	@ParameterizedTest
	@CsvSource({ "false,false,", "false,true,", "true,false,", "true,true,", "false,true,Archive Data",
			"true,true,Archive Data" })
	void guiAdapterCreatesSharedJobExportsAndImportsWithOnlyTargetConnection(boolean createMissing,
			boolean packageFolder, String targetSchema) throws Exception {
		final var source = new PlanningJdbc.Spec("jdbc:hsqldb:mem:csv_gui_source_" + UUID.randomUUID(), "SA", "");
		final var target = new PlanningJdbc.Spec("jdbc:hsqldb:mem:csv_gui_target_" + UUID.randomUUID(), "SA", "");
		try (var from = DriverManager.getConnection(source.jdbcUrl(), "SA", "");
				var to = DriverManager.getConnection(target.jdbcUrl(), "SA", "")) {
			try (var sql = from.createStatement()) {
				sql.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(40))");
				sql.execute("INSERT INTO ITEMS VALUES(1, 'one'),(2, NULL)");
				sql.execute("CREATE TABLE LOBS(ID INTEGER NOT NULL PRIMARY KEY, CONTENT BLOB)");
			}
			final String targetTable = targetSchema == null ? "ITEMS" : "\"" + targetSchema + "\".ITEMS";
			if (targetSchema != null)
				try (var sql = to.createStatement()) {
					sql.execute("CREATE SCHEMA \"" + targetSchema + "\" AUTHORIZATION DBA");
				}
			if (!createMissing)
				try (var sql = to.createStatement()) {
					sql.execute("CREATE TABLE " + targetTable + "(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(40))");
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
					TransferJobConfiguration.TemporalPolicy.WARN, TransferJobConfiguration.CompatibilityPolicy.WARN,
					targetSchema, prepared.directory().resolve("transfer.yaml"));
			assertEquals(targetSchema, job.configuration().targetSchema());
			assertFalse(job.text().contains(source.jdbcUrl()));
			final var exported = (TransferFilePackageIO.Snapshot) service.perform(FileTransferPanel.Action.EXPORT,
					job.file(), null, source, new char[0], "", null);
			final Path selected = packageFolder ? exported.file().getParent() : exported.file();
			final var inspected = (FileTransferDesktopService.Inspected) service
					.perform(FileTransferPanel.Action.INSPECT, selected, null, null, new char[0], "", null);
			assertEquals(exported, inspected.snapshot());
			assertFalse(inspected.valuesValidated());
			assertEquals(job.configuration(), inspected.configuration());
			final var validated = (FileTransferDesktopService.Inspected) service
					.perform(FileTransferPanel.Action.INSPECT_DATA, selected, null, null, new char[0], "", null);
			assertTrue(validated.valuesValidated());
			assertEquals(job.configuration(), validated.configuration());
			final var review = (FileTransferDesktopService.Reviewed) service.perform(FileTransferPanel.Action.REVIEW,
					selected, null, target, new char[0], "", null);
			assertEquals(exported.file(), review.review().manifestFile());
			assertEquals(createMissing, review.schemaSqlDraft() != null);
			if (createMissing)
				assertTrue(review.schemaSqlDraft().contains("-- Job: " + exported.manifest().jobFingerprint()));
			final var result = (TransferFileImportService.Outcome) service.perform(FileTransferPanel.Action.IMPORT,
					selected, directory, target, new char[0], "", review);
			assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
			assertTrue(result.verificationRequested());
			assertFalse(result.verification().isEmpty());
			assertEquals(createMissing, result.schemaPreparation().completedStatements() > 0);
			assertTrue(Files.isRegularFile(result.auditFile()));
			assertEquals(review.schemaSqlDraft(), FileTransferDesktopService.draftFor(result, review));
			assertFalse(Files.readString(result.auditFile()).contains("schemaSqlDraft"));
			if (createMissing) {
				var changed = new TransferFileImportService.Outcome(result.manifestFingerprint(),
						result.planFingerprint(), result.phase(), result.copyResult(), result.verificationRequested(),
						result.verification(), result.committedProgress(), null, result.auditFile(),
						new TransferSchemaPreparation.Progress(List.of(), 0, null));
				assertNull(FileTransferDesktopService.draftFor(changed, review));
			}
			try (var sql = to.createStatement();
					var rows = sql.executeQuery("SELECT ID, NAME FROM " + targetTable + " ORDER BY ID")) {
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
	void offlineInspectionRejectsChangedPackageDataBeforeAnyConnectionAndClearsBuffers() throws Exception {
		final var service = new FileTransferDesktopService();
		final var exported = exportSingleItem();
		Files.writeString(exported.file().getParent().resolve(exported.manifest().tables().getFirst().file()),
				"changed");
		final char[] password = "unused-secret".toCharArray();
		final var failure = assertThrows(IllegalArgumentException.class,
				() -> service.perform(FileTransferPanel.Action.INSPECT, exported.file().getParent(), null, null,
						password, "", null));
		assertTrue(failure.getMessage().contains("Package inspection failed"));
		assertNull(failure.getCause());
		assertFalse(failure.getMessage().contains("unused-secret"));
		assertArrayEquals(new char[password.length], password);
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
	void valueInspectionRejectsBadValuesOrRowCountsEvenWhenPackageHashesMatch(boolean wrongRowCount) throws Exception {
		final var exported = exportSingleItem();
		final var original = exported.manifest();
		final var table = original.tables().getFirst();
		final var csv = exported.file().getParent().resolve(table.file());
		if (!wrongRowCount)
			Files.writeString(csv, "ID\nnot-an-integer\n");
		final var changedTable = new TransferFilePackageIO.TableFile(table.taskId(), table.catalog(), table.schema(),
				table.table(), table.columns(), table.file(), wrongRowCount ? table.rows() + 1 : table.rows(),
				Files.size(csv),
				"sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(Files.readAllBytes(csv)));
		Files.writeString(exported.file(),
				new com.sqlapp.util.JsonConverter().toJsonString(new TransferFilePackageIO.Manifest(
						original.formatVersion(), original.jobFingerprint(), original.schemaFingerprint(),
						original.files(), List.of(changedTable), original.warnings())));
		final var service = new FileTransferDesktopService();
		assertInstanceOf(FileTransferDesktopService.Inspected.class,
				service.perform(FileTransferPanel.Action.INSPECT, exported.file(), null, null, new char[0], "", null));
		final var failure = assertThrows(IllegalArgumentException.class, () -> service
				.perform(FileTransferPanel.Action.INSPECT_DATA, exported.file(), null, null, new char[0], "", null));
		assertTrue(failure.getMessage().contains("CSV value validation failed"));
		assertNull(failure.getCause());
		assertFalse(failure.getMessage().contains("not-an-integer"));
	}

	private TransferFilePackageIO.Snapshot exportSingleItem() throws Exception {
		final var source = new PlanningJdbc.Spec("jdbc:hsqldb:mem:csv_inspect_" + UUID.randomUUID(), "SA", "");
		try (var connection = DriverManager.getConnection(source.jdbcUrl(), "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
			sql.execute("INSERT INTO ITEMS VALUES(1)");
		}
		final var job = Files.writeString(directory.resolve("transfer.yaml"),
				"formatVersion: 1\nsource: source\ntarget: target\ntables: [ITEMS]\nschemaMode: REQUIRE_EXISTING\ndataMode: FILE\nfiles: {directory: packages, nullValue: __NULL__}\n");
		return (TransferFilePackageIO.Snapshot) new FileTransferDesktopService()
				.perform(FileTransferPanel.Action.EXPORT, job, null, source, new char[0], "", null);
	}

	@Test
	void folderWithoutManifestFailsBeforeOpeningConnectionAndClearsPassword() {
		final char[] password = "private-value".toCharArray();
		final var failure = assertThrows(IllegalArgumentException.class,
				() -> new FileTransferDesktopService().perform(FileTransferPanel.Action.REVIEW, directory, null,
						new PlanningJdbc.Spec("jdbc:missing:private-value", "user", ""), password, "", null));
		assertTrue(failure.getMessage().contains("folder must contain manifest.json directly"));
		assertFalse(failure.getMessage().contains("private-value"));
		assertNull(failure.getCause());
		assertArrayEquals(new char[password.length], password);
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
