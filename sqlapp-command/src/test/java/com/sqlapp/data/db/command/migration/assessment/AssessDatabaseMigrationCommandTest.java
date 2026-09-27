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
	void resolvesFingerprintBoundMappingAndValidatesObservedOracleCapacity() throws Exception {
		final var command = command("mapping.accdb", "oracle", "19c");
		Files.delete(command.getInputFile().toPath());
		try (var database = DatabaseBuilder.create(Database.FileFormat.V2010, command.getInputFile())) {
			final var table = new io.github.spannm.jackcess.TableBuilder("顧客")
					.addColumn(new io.github.spannm.jackcess.ColumnBuilder("名前", io.github.spannm.jackcess.DataType.TEXT))
					.addColumn(new io.github.spannm.jackcess.ColumnBuilder("金額", io.github.spannm.jackcess.DataType.NUMERIC).withPrecision(10).withScale(2))
					.toTable(database);
			table.addRow("長い名前", new java.math.BigDecimal("123.45"));
			table.addRow(null, null);
		}
		final String fingerprint = AssessMigrationCommand.fingerprint(command.getInputFile());
		final Path mapping = directory.resolve("mapping.yaml");
		Files.writeString(mapping, ("""
				format: sqlapp-database-migration-mapping
				version: 1
				sourceFingerprint: %s
				targetDatabase: oracle
				targetVersion: 19c
				tables:
				  - sourceTable: 顧客
				    targetSchema: APP
				    targetTable: CUSTOMERS
				    columns:
				      - sourceColumn: 名前
				        targetColumn: NAME
				        targetType: VARCHAR2(2 CHAR)
				        nullable: false
				        conversion: preserve Unicode text
				      - sourceColumn: 金額
				        targetColumn: AMOUNT
				        targetType: NUMBER(4,2)
				""").formatted(fingerprint));
		command.setMappingFile(mapping.toFile());
		command.setHtmlOutputFile(directory.resolve("mapping.html").toFile());
		command.setScanData(true);
		assertThrows(CommandException.class, command::run);
		assertEquals(3, command.getReport().formatVersion());
		assertEquals("oracle", command.getReport().targetMapping().targetDatabase());
		assertEquals("顧客", command.getReport().targetMapping().tables().getFirst().sourceTable().name());
		assertEquals("CUSTOMERS", command.getReport().targetMapping().tables().getFirst().targetTable());
		assertEquals(AssessMigrationCommand.fingerprint(mapping.toFile()), command.getReport().mappingFingerprint());
		assertTrue(command.getReport().assessment().findings().stream().anyMatch(f -> f.ruleId().equals("access.oracle.mapping.observed-text-overflow")));
		assertTrue(command.getReport().assessment().findings().stream().anyMatch(f -> f.ruleId().equals("access.oracle.mapping.observed-number-overflow")));
		assertTrue(command.getReport().assessment().findings().stream().anyMatch(f -> f.ruleId().equals("access.oracle.mapping.observed-null")));
		final String json = Files.readString(command.getOutputFile().toPath());
		assertTrue(json.contains("mappingFingerprint"));
		assertTrue(json.contains("targetMapping"));
		assertTrue(Files.readString(command.getHtmlOutputFile().toPath()).contains("Resolved target mapping"));

		Files.writeString(mapping, Files.readString(mapping).replace(fingerprint, "wrong"));
		assertThrows(CommandException.class, command::run);
		assertEquals(json, Files.readString(command.getOutputFile().toPath()));
		assertNull(command.getReport());
		Files.writeString(mapping, Files.readString(mapping).replace("wrong", fingerprint) + "unknownTypo: true\n");
		assertThrows(CommandException.class, command::run);
		assertEquals(json, Files.readString(command.getOutputFile().toPath()));
	}

	@Test
	void writesOptionalHtmlReviewAtomicallyAndRejectsPathCollisions() throws Exception {
		final var command = command("html.accdb", "oracle", "19c");
		Files.delete(command.getInputFile().toPath());
		try (var database = DatabaseBuilder.create(Database.FileFormat.V2010, command.getInputFile())) {
			new io.github.spannm.jackcess.TableBuilder("T")
					.addColumn(new io.github.spannm.jackcess.ColumnBuilder("C", io.github.spannm.jackcess.DataType.TEXT))
					.toTable(database);
		}
		final var html = directory.resolve("assessment.html").toFile();
		command.setHtmlOutputFile(html);
		command.setFailOnBlockers(false);
		command.run();
		final String text = Files.readString(html.toPath());
		assertTrue(text.startsWith("<!doctype html>"));
		assertTrue(text.contains("Database migration assessment"));
		assertTrue(text.contains("Oracle 19c"));
		assertTrue(Files.readString(command.getOutputFile().toPath()).startsWith("{"));

		command.setHtmlOutputFile(command.getOutputFile());
		final String json = Files.readString(command.getOutputFile().toPath());
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("distinct"));
		assertEquals(json, Files.readString(command.getOutputFile().toPath()));
		command.setHtmlOutputFile(command.getInputFile());
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("distinct"));
	}

	@Test
	void publishesOrphanBlockersAndStructuredCountsForBothTargets() throws Exception {
		final var command = command("orphan.accdb", "oracle", "19c");
		final var html = directory.resolve("orphan.html").toFile();
		command.setHtmlOutputFile(html);
		Files.delete(command.getInputFile().toPath());
		try (var database = DatabaseBuilder.create(Database.FileFormat.V2010, command.getInputFile())) {
			final var parent = new io.github.spannm.jackcess.TableBuilder("Parent")
					.addColumn(new io.github.spannm.jackcess.ColumnBuilder("ID", io.github.spannm.jackcess.DataType.LONG)).toTable(database);
			final var child = new io.github.spannm.jackcess.TableBuilder("Child")
					.addColumn(new io.github.spannm.jackcess.ColumnBuilder("PID", io.github.spannm.jackcess.DataType.LONG)).toTable(database);
			new io.github.spannm.jackcess.RelationshipBuilder(parent, child).addColumns("ID", "PID").withName("FK_child").toRelationship(database);
			child.addRow(123);
		}
		command.run();
		assertEquals("REVIEW_REQUIRED", command.getReport().status());
		command.setScanData(true);
		for (final String target : new String[] { "oracle", "sqlserver" }) {
			command.setTargetDatabase(target);
			command.setTargetVersion(target.equals("oracle") ? "19c" : "2022");
			assertThrows(CommandException.class, command::run);
			assertEquals("BLOCKED", command.getReport().status());
			assertTrue(Files.readString(html.toPath()).contains("access.data.orphan-key"));
			assertEquals(1L, command.getReport().dataProfile().integrityChecks().getFirst().violationRows());
			final var json = Files.readString(command.getOutputFile().toPath());
			assertTrue(json.contains("integrityChecks"));
			assertTrue(json.contains("access.data.orphan-key"));
		}
		command.setFailOnBlockers(false);
		command.run();
		assertEquals("BLOCKED", command.getReport().status());
	}

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
