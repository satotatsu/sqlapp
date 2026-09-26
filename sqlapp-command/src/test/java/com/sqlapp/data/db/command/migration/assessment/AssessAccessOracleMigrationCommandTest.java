/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.data.db.dialect.oracle.migration.AccessOracleMigrationAssessment;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Method;

import io.github.spannm.jackcess.ColumnBuilder;
import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.DatabaseBuilder;
import io.github.spannm.jackcess.DataType;
import io.github.spannm.jackcess.TableBuilder;

class AssessAccessOracleMigrationCommandTest {
	@TempDir Path directory;

	private AssessAccessOracleMigrationCommand command(final boolean linked) throws Exception {
		final var input = directory.resolve("業務.accdb");
		try (final var database = DatabaseBuilder.create(Database.FileFormat.V2010, input.toFile())) {
			new TableBuilder("顧客")
					.addColumn(new ColumnBuilder("ID", DataType.LONG).withAutoNumber(true))
					.addColumn(new ColumnBuilder("名前", DataType.TEXT))
					.addColumn(new ColumnBuilder("金額", DataType.MONEY))
					.toTable(database).addRow(io.github.spannm.jackcess.Column.AUTO_NUMBER, "秘密の行データ", 12.34);
			if (linked) {
				database.createLinkedTable("外部顧客", directory.resolve("missing-secret-source.accdb").toString(), "REMOTE");
			}
		}
		final var command = new AssessAccessOracleMigrationCommand();
		command.setInputFile(input.toFile());
		command.setOutputFile(directory.resolve("report.json").toFile());
		command.setTargetVersion("19c");
		return command;
	}

	@Test
	void assessesLocalFileWithoutReadingLinksOrExposingRowsAndPreservesSource() throws Exception {
		final var command = command(true);
		final var before = Files.readAllBytes(command.getInputFile().toPath());
		command.run();
		final var report = command.getReport();
		assertEquals("REVIEW_REQUIRED", report.status());
		assertFalse(report.dataScanned());
		assertFalse(report.relationshipsCollected());
		assertEquals("Oracle", report.targetProduct());
		assertTrue(report.sourceFingerprint().matches("sha256:[a-f0-9]{64}"));
		final var json = Files.readString(command.getOutputFile().toPath());
		assertTrue(json.contains("外部顧客"));
		assertTrue(json.contains("NUMBER(19,4)"));
		assertTrue(json.contains("access.relationship-coverage"));
		assertFalse(json.contains("missing-secret-source"));
		assertFalse(json.contains("秘密の行データ"));
		assertArrayEquals(before, Files.readAllBytes(command.getInputFile().toPath()));
		command.run();
		assertEquals(json, Files.readString(command.getOutputFile().toPath()));
	}

	@Test
	void localOnlySnapshotMarksRelationshipsCollectedAndManualCoverageExplicit() throws Exception {
		final var command = command(false);
		command.run();
		assertTrue(command.getReport().relationshipsCollected());
		assertTrue(command.getReport().assessment().findings().stream().anyMatch(f -> f.ruleId().equals("access.application-coverage")));
	}

	@Test
	void rejectsBadConfigurationAndPreservesPreviousReport() throws Exception {
		final var command = command(false);
		command.run();
		final String before = Files.readString(command.getOutputFile().toPath());
		command.setTargetVersion("unknown");
		assertThrows(CommandException.class, command::run);
		assertNull(command.getReport());
		assertEquals(before, Files.readString(command.getOutputFile().toPath()));
		command.setTargetVersion("19c");
		command.setOutputFile(command.getInputFile());
		final byte[] source = Files.readAllBytes(command.getInputFile().toPath());
		assertThrows(CommandException.class, command::run);
		assertArrayEquals(source, Files.readAllBytes(command.getInputFile().toPath()));
		command.setInputFile(directory.resolve("missing.accdb").toFile());
		assertThrows(CommandException.class, command::run);
	}

	@Test
	void rejectsCorruptFilesWithoutPublishingReport() throws Exception {
		final var command = command(false);
		Files.writeString(command.getInputFile().toPath(), "not an Access database");
		assertThrows(CommandException.class, command::run);
		assertFalse(command.getOutputFile().exists());
		assertNull(command.getReport());
	}

	@Test
	void publishesComplexTypeBlockerBeforeFailingAndCanDisableOnlyTheGate() throws Exception {
		final var command = new AssessAccessOracleMigrationCommand();
		command.setOutputFile(directory.resolve("blockers.json").toFile());
		final var schema = new Schema("").setProductName("Microsoft Access");
		final var table = new Table("Documents");
		final var column = new Column("Attachments");
		column.getSpecifics().put("access.sourceType", "COMPLEX_TYPE");
		table.getColumns().add(column);
		schema.getTables().add(table);
		final var assessment = AccessOracleMigrationAssessment.assess(schema, "19c");
		final var report = new AssessAccessOracleMigrationCommand.Report(1, "fixture-fingerprint", "Microsoft Access",
				"Oracle", "19c", Method.LOGICAL_MIGRATION, false, true, "BLOCKED", assessment);
		assertThrows(CommandException.class, () -> command.writeReport(report));
		assertSame(report, command.getReport());
		final var json = Files.readString(command.getOutputFile().toPath());
		assertTrue(json.contains("access.oracle.complex-type"));
		assertTrue(json.contains("BLOCKED"));
		command.setFailOnBlockers(false);
		command.writeReport(report);
		assertEquals(json, Files.readString(command.getOutputFile().toPath()));
	}
}
