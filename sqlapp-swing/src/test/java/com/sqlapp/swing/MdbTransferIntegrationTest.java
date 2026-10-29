/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.transfer.*;

/**
 * Generated local Access fixture and embedded HSQL only; no external database.
 */
class MdbTransferIntegrationTest {
	@TempDir
	Path directory;

	@ParameterizedTest
	@CsvSource({ "false,false", "true,false", "true,true" })
	void accessFileDiscoversReviewsAndTransfersRelationshipsThroughSharedJob(boolean japanese, boolean schemaOnly)
			throws Exception {
		String parent = japanese ? "親 顧客" : "PARENTS";
		String child = japanese ? "子 明細" : "CHILDREN";
		String id = japanese ? "識別番号" : "ID";
		String name = japanese ? "顧客 名" : "NAME";
		String parentId = japanese ? "親番号" : "PARENT_ID";
		var file = directory.resolve(japanese ? schemaOnly ? "日本語 データ.accdb" : "日本語 データ.mdb" : "source database.mdb");
		try (var connection = DriverManager.getConnection("jdbc:ucanaccess://" + file.toAbsolutePath()
				+ (schemaOnly ? ";newDatabaseVersion=V2010" : ";newDatabaseVersion=V2000"));
				var statement = connection.createStatement()) {
			System.out.println("Access integration driver: " + connection.getMetaData().getDriverVersion());
			for (String suffix : List.of("", "2")) {
				int key = suffix.isEmpty() ? 1 : 11;
				statement.execute("CREATE TABLE [" + parent + suffix + "] ([" + id + "] INTEGER NOT NULL PRIMARY KEY, ["
						+ name + "] VARCHAR(40))");
				statement.execute("CREATE TABLE [" + child + suffix + "] ([" + id + "] INTEGER NOT NULL PRIMARY KEY, ["
						+ parentId + "] INTEGER, CONSTRAINT FK_PARENT" + suffix + " FOREIGN KEY ([" + parentId
						+ "]) REFERENCES [" + parent + suffix + "]([" + id + "]))");
				statement.execute("INSERT INTO [" + parent + suffix + "] VALUES (" + key + ", '日本語データ')");
				statement.execute("INSERT INTO [" + child + suffix + "] VALUES (" + (key + 1) + ", " + key + ")");
			}
		}
		var source = new PlanningJdbc.Spec(MdbSourceFile.jdbcUrl(file), "", MdbSourceFile.DRIVER);
		var sourceBytes = Files.readAllBytes(file);
		var target = new PlanningJdbc.Spec("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "");
		var service = new DirectTransferDesktopService();
		var discovered = (DataMigrationDraftService.Prepared) service.perform(DirectTransferPanel.Action.DISCOVER,
				new DirectTransferPanel.Request(null, directory, source, null, "", null), new char[0], new char[0]);
		var tables = discovered.schema().tables().stream().map(table -> table.table()).toList();
		assertEquals(4, tables.size());
		var saved = schemaOnly
				? new TransferJobDraftBuilder().saveSchemaOnly(discovered.schema(), tables,
						TransferJobConfiguration.CompatibilityPolicy.WARN, "PUBLIC",
						discovered.directory().resolve("transfer.yaml"))
				: new TransferJobDraftBuilder().saveDirect(discovered.schema(), tables, 100, true,
						TransferJobConfiguration.CompatibilityPolicy.WARN, "PUBLIC",
						TransferJobConfiguration.SchemaMode.CREATE_MISSING,
						discovered.directory().resolve("transfer.yaml"));
		var review = (DirectTransferDesktopService.Reviewed) service.perform(DirectTransferPanel.Action.REVIEW,
				new DirectTransferPanel.Request(saved.file(), directory, source, target, "", null), new char[0],
				new char[0]);
		assertNotNull(review.schemaSqlDraft());
		assertTrue(review.schemaSqlDraft().contains(TransferSchemaChangeChecks.NAME_ADJUSTMENT));
		assertTrue(Files.readString(review.report()).contains(TransferSchemaChangeChecks.NAME_ADJUSTMENT));
		var result = (DirectTransferDesktopService.Executed) service.perform(DirectTransferPanel.Action.EXECUTE,
				new DirectTransferPanel.Request(saved.file(), directory, source, target, "", review), new char[0],
				new char[0]);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.outcome().phase());
		assertTrue(Files.readString(result.report()).contains(TransferSchemaChangeChecks.NAME_ADJUSTMENT));
		if (schemaOnly)
			assertNull(result.outcome().verificationResult());
		else
			assertTrue(result.outcome().verificationResult().isMatch());
		try (var connection = DriverManager.getConnection(target.jdbcUrl(), "SA", "");
				var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT P.\"" + name + "\" FROM \"" + parent + "\" P JOIN \"" + child
						+ "\" C ON P.\"" + id + "\"=C.\"" + parentId + "\" WHERE C.\"" + id + "\"=2")) {
			if (!schemaOnly) {
				assertTrue(rows.next());
				assertEquals("日本語データ", rows.getString(1));
			}
			assertFalse(rows.next());
			for (String suffix : List.of("", "2"))
				try (var keys = connection.getMetaData().getImportedKeys(null, "PUBLIC", child + suffix)) {
					assertTrue(keys.next());
					assertEquals(parent + suffix, keys.getString("PKTABLE_NAME"));
				}
		}
		assertArrayEquals(sourceBytes, Files.readAllBytes(file),
				"Migration must leave the generated source MDB unchanged");
	}
}
