/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.exceptions.CommandException;
import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.DatabaseBuilder;

class AssessDatabaseMigrationCommandTest {
	@TempDir Path directory;

	@Test
	void optInScanWritesVersionedAggregatesAndPublishesObservedOracleBlocker() throws Exception {
		final var command = command("scan.accdb", "oracle", "19c");
		Files.delete(command.getInputFile().toPath());
		try (var database = DatabaseBuilder.create(Database.FileFormat.V2010, command.getInputFile())) {
			final var table = new io.github.spannm.jackcess.TableBuilder("T")
					.addColumn(new io.github.spannm.jackcess.ColumnBuilder("C", io.github.spannm.jackcess.DataType.TEXT)
							.withProperty(io.github.spannm.jackcess.PropertyMap.ALLOW_ZERO_LEN_PROP, true)
							.withProperty(io.github.spannm.jackcess.PropertyMap.REQUIRED_PROP, true))
					.addColumn(new io.github.spannm.jackcess.ColumnBuilder("D", io.github.spannm.jackcess.DataType.SHORT_DATE_TIME))
					.toTable(database);
			table.addRow("", java.time.LocalDateTime.of(1600, 1, 1, 0, 0));
			table.addRow("private-text", java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
		}
		final byte[] before = Files.readAllBytes(command.getInputFile().toPath());
		assertFalse(command.isScanData());
		command.run();
		assertEquals(1, command.getReport().formatVersion());
		assertNull(command.getReport().dataProfile());
		assertFalse(Files.readString(command.getOutputFile().toPath()).contains("dataProfile"));
		command.setScanData(true);
		assertThrows(CommandException.class, command::run);
		assertEquals("BLOCKED", command.getReport().status());
		assertEquals(2, command.getReport().formatVersion());
		assertTrue(command.getReport().dataScanned());
		assertEquals(2, command.getReport().dataProfile().tables().getFirst().rowCount());
		final var json = Files.readString(command.getOutputFile().toPath());
		assertTrue(json.contains("observed-empty-string"));
		assertTrue(json.contains("maximumUtf8Bytes"));
		assertFalse(json.contains("private-text"));
		assertFalse(json.contains("access.data-not-scanned"));
		command.setFailOnBlockers(false);
		command.run();
		assertEquals(json, Files.readString(command.getOutputFile().toPath()));
		command.setTargetDatabase("sqlserver");
		command.setTargetVersion("2022");
		command.setFailOnBlockers(true);
		command.run();
		assertEquals("REVIEW_REQUIRED", command.getReport().status());
		assertTrue(command.getReport().assessment().findings().stream()
				.anyMatch(f -> f.ruleId().equals("access.sqlserver.observed-legacy-datetime-range")));
		assertArrayEquals(before, Files.readAllBytes(command.getInputFile().toPath()));
	}

	@Test
	void scanOptionCannotBeIgnoredByLegacyProviders() throws Exception {
		final var command = command("input.assessment-fixture", "fixture", "1");
		Files.writeString(command.getOutputFile().toPath(), "previous");
		command.setScanData(true);
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("scanData is not supported"));
		assertNull(command.getReport());
		assertEquals("previous", Files.readString(command.getOutputFile().toPath()));
	}

	@Test
	void sqlServerReportComposesAccessCoverageAndPreservesTheFile() throws Exception {
		final var command = command("sqlserver.accdb", "sqlserver", "2022");
		Files.delete(command.getInputFile().toPath());
		try (var database = DatabaseBuilder.create(Database.FileFormat.V2010, command.getInputFile())) {
			new io.github.spannm.jackcess.TableBuilder("顧客")
					.addColumn(new io.github.spannm.jackcess.ColumnBuilder("名前", io.github.spannm.jackcess.DataType.TEXT))
					.toTable(database).addRow("非公開データ");
			database.createLinkedTable("外部", directory.resolve("missing.accdb").toString(), "T");
		}
		final byte[] before = Files.readAllBytes(command.getInputFile().toPath());
		command.run();
		assertEquals("Microsoft SQL Server", command.getReport().targetProduct());
		assertFalse(command.getReport().dataScanned());
		assertFalse(command.getReport().relationshipsCollected());
		final var json = Files.readString(command.getOutputFile().toPath());
		assertTrue(json.contains("access.sqlserver.type"));
		assertTrue(json.contains("access.linked-table"));
		assertTrue(json.contains("access.application-coverage"));
		assertFalse(json.contains("access.oracle."));
		assertFalse(json.contains("非公開データ"));
		assertArrayEquals(before, Files.readAllBytes(command.getInputFile().toPath()));
		command.setTargetVersion("2025");
		assertThrows(CommandException.class, command::run);
		assertEquals(json, Files.readString(command.getOutputFile().toPath()));
	}

	private AssessDatabaseMigrationCommand command(String file, String target, String version) throws Exception {
		final var command = new AssessDatabaseMigrationCommand();
		command.setInputFile(Files.writeString(directory.resolve(file), "fixture").toFile());
		command.setOutputFile(directory.resolve("report.json").toFile());
		command.setTargetDatabase(target);
		command.setTargetVersion(version);
		return command;
	}

	@Test
	void composesIndependentProvidersPreservingSourceBlockersAndNativeDetails() throws Exception {
		final var command = command("input.assessment-fixture", "fixture", "1");
		assertThrows(CommandException.class, command::run);
		assertEquals("Fixture Source", command.getReport().sourceProduct());
		assertEquals("Fixture Target", command.getReport().targetProduct());
		assertEquals("BLOCKED", command.getReport().status());
		final var json = Files.readString(command.getOutputFile().toPath());
		assertTrue(json.contains("fixture.source-blocker"));
		assertTrue(json.contains("fixture.target-review"));
		assertTrue(json.contains("preserved"));
		command.setFailOnBlockers(false);
		command.run();
		assertEquals(json, Files.readString(command.getOutputFile().toPath()));
	}

	@Test
	void rejectsMissingTargetAndUnsupportedSourceTargetAndVersionWithoutReplacingReport() throws Exception {
		final var command = command("input.assessment-fixture", "fixture", "1");
		Files.writeString(command.getOutputFile().toPath(), "previous");
		for (String database : new String[] { null, "", "oracle", "postgres" }) {
			command.setTargetDatabase(database);
			assertThrows(CommandException.class, command::run);
			assertNull(command.getReport());
			assertEquals("previous", Files.readString(command.getOutputFile().toPath()));
		}
		command.setTargetDatabase("fixture");
		command.setTargetVersion("unknown");
		assertThrows(CommandException.class, command::run);
		command.setInputFile(Files.writeString(directory.resolve("unsupported.xyz"), "fixture").toFile());
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("source provider"));
	}

	@Test
	void genericAndCompatibilityEntryPointsProduceEquivalentAccessReports() throws Exception {
		final var command = command("source.accdb", "ORACLE", "19C");
		Files.delete(command.getInputFile().toPath());
		try (var database = DatabaseBuilder.create(Database.FileFormat.V2010, command.getInputFile())) { }
		command.run();
		final var generic = command.getReport();
		assertEquals("Oracle", generic.targetProduct());
		assertEquals("19c", generic.targetVersion());
		final var alias = new AssessAccessOracleMigrationCommand();
		alias.setInputFile(command.getInputFile());
		alias.setOutputFile(command.getOutputFile());
		alias.setTargetVersion("19c");
		alias.run();
		assertEquals(generic, alias.getReport());
		assertThrows(IllegalArgumentException.class, () -> alias.setTargetDatabase("postgres"));
		command.setTargetDatabase("postgres");
		command.setTargetVersion("16");
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("found 0"));
	}
}
