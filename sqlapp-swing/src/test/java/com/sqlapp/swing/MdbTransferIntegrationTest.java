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
	@org.junit.jupiter.params.provider.ValueSource(strings = { "ID", "行ID" })
	void keylessAccessUsesDefaultOrConfiguredTargetIdentityWithoutChangingTheFile(String keyName) throws Exception {
		var file = directory.resolve("keyless.accdb");
		try (var connection = DriverManager
				.getConnection("jdbc:ucanaccess://" + file.toAbsolutePath() + ";newDatabaseVersion=V2010");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE [NO_KEY] ([VALUE] INTEGER)");
			for (var value : List.of("1", "1", "NULL"))
				statement.execute("INSERT INTO [NO_KEY] VALUES(" + value + ")");
		}
		byte[] original = Files.readAllBytes(file);
		var source = new PlanningJdbc.Spec(MdbSourceFile.jdbcUrl(file), "", MdbSourceFile.DRIVER);
		var target = new PlanningJdbc.Spec("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "");
		var service = new DirectTransferDesktopService();
		var discovered = (DataMigrationDraftService.Prepared) service.perform(DirectTransferPanel.Action.DISCOVER,
				new DirectTransferPanel.Request(null, directory, source, null, "", null), new char[0], new char[0]);
		var tables = discovered.schema().tables().stream().map(table -> table.table()).toList();
		var saved = new TransferJobDraftBuilder().saveDirect(discovered.schema(), tables, 1, true, null, "PUBLIC",
				TransferJobConfiguration.SchemaMode.CREATE_MISSING_WITH_IDENTITY,
				discovered.directory().resolve("transfer.yaml"), keyName.equals("ID") ? null : keyName);
		var reviewed = (DirectTransferDesktopService.Reviewed) service.perform(DirectTransferPanel.Action.REVIEW,
				new DirectTransferPanel.Request(saved.file(), directory, source, target, "", null), new char[0],
				new char[0]);
		var executed = (DirectTransferDesktopService.Executed) service.perform(DirectTransferPanel.Action.EXECUTE,
				new DirectTransferPanel.Request(saved.file(), directory, source, target, "", reviewed), new char[0],
				new char[0]);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, executed.outcome().phase());
		assertTrue(executed.outcome().verificationResult().isMatch());
		try (var connection = DriverManager.getConnection(target.jdbcUrl(), "SA", "");
				var keys = connection.getMetaData().getPrimaryKeys(null, "PUBLIC", "NO_KEY")) {
			assertTrue(keys.next());
			assertEquals(keyName, keys.getString("COLUMN_NAME"));
		}
		try (var connection = DriverManager.getConnection(target.jdbcUrl(), "SA", "");
				var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT COUNT(*), COUNT(DISTINCT \"" + keyName + "\") FROM NO_KEY")) {
			rows.next();
			assertEquals(3, rows.getInt(1));
			assertEquals(3, rows.getInt(2));
		}
		assertArrayEquals(original, Files.readAllBytes(file));
	}

	@ParameterizedTest
	@CsvSource({ "false,false", "true,false", "true,true" })
	void accessFileDiscoversReviewsAndTransfersRelationshipsThroughSharedJob(boolean japanese, boolean schemaOnly)
			throws Exception {
		String parent = japanese ? "親 顧客" : "PARENTS";
		String child = japanese ? "子 明細" : "CHILDREN";
		String id = japanese ? "識別番号" : "ID";
		String name = japanese ? "顧客 名" : "NAME";
		String parentId = japanese ? "親番号" : "PARENT_ID";
		String created = japanese ? "登録 日時" : "CREATED_AT";
		var file = directory.resolve(japanese ? schemaOnly ? "日本語 データ.accdb" : "日本語 データ.mdb" : "source database.mdb");
		try (var connection = DriverManager.getConnection("jdbc:ucanaccess://" + file.toAbsolutePath()
				+ (schemaOnly ? ";newDatabaseVersion=V2010" : ";newDatabaseVersion=V2000"));
				var statement = connection.createStatement()) {
			System.out.println("Access integration driver: " + connection.getMetaData().getDriverVersion());
			for (String suffix : List.of("", "2")) {
				int key = suffix.isEmpty() ? 1 : 11;
				statement.execute("CREATE TABLE [" + parent + suffix + "] ([" + id + "] INTEGER NOT NULL PRIMARY KEY, ["
						+ name + "] VARCHAR(40), [" + created + "] DATETIME)");
				statement.execute("CREATE TABLE [" + child + suffix + "] ([" + id + "] INTEGER NOT NULL PRIMARY KEY, ["
						+ parentId + "] INTEGER, CONSTRAINT FK_PARENT" + suffix + " FOREIGN KEY ([" + parentId
						+ "]) REFERENCES [" + parent + suffix + "]([" + id + "]))");
				try (var insert = connection.prepareStatement("INSERT INTO [" + parent + suffix + "] VALUES (?,?,?)")) {
					insert.setInt(1, key);
					insert.setString(2, "日本語データ");
					insert.setTimestamp(3, suffix.isEmpty() ? java.sql.Timestamp.valueOf("2001-02-03 04:05:06") : null);
					insert.executeUpdate();
				}
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
				var rows = statement.executeQuery(
						"SELECT P.\"" + name + "\", P.\"" + created + "\" FROM \"" + parent + "\" P JOIN \"" + child
								+ "\" C ON P.\"" + id + "\"=C.\"" + parentId + "\" WHERE C.\"" + id + "\"=2")) {
			if (!schemaOnly) {
				assertTrue(rows.next());
				assertEquals("日本語データ", rows.getString(1));
				assertEquals(java.sql.Timestamp.valueOf("2001-02-03 04:05:06"), rows.getTimestamp(2));
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
