/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Method;
import com.sqlapp.exceptions.CommandException;

class AssessMigrationCommandTest {
	@TempDir Path directory;

	private AssessMigrationCommand command() throws Exception {
		final var input = directory.resolve("schema.xml").toFile();
		new Schema("日本語").setProductName("Oracle").setProductMajorVersion(10).writeXml(input);
		final var command = new AssessMigrationCommand();
		command.setSchemaFile(input);
		command.setOutputFile(directory.resolve("report.json").toFile());
		command.setTargetVersion("26ai");
		command.setMigrationMethod(Method.DIRECT_UPGRADE);
		return command;
	}

	@Test
	void writesEvidenceBeforeFailingGateAndPreservesInput() throws Exception {
		final var command = command();
		final byte[] before = Files.readAllBytes(command.getSchemaFile().toPath());
		assertThrows(CommandException.class, command::run);
		final String json = Files.readString(command.getOutputFile().toPath());
		assertTrue(json.contains("BLOCKED"));
		assertTrue(json.contains("日本語"));
		assertTrue(json.contains("DOCUMENTED_RULE"));
		assertTrue(command.getReport().schemaFingerprint().matches("sha256:[a-f0-9]{64}"));
		assertArrayEquals(before, Files.readAllBytes(command.getSchemaFile().toPath()));
		command.setFailOnBlockers(false);
		command.run();
		assertEquals(json, Files.readString(command.getOutputFile().toPath()));
	}

	@Test
	void assessesCatalogAndLogicalMigrationWithoutClaimingReadiness() throws Exception {
		final var command = command();
		final var catalog = new Catalog("DB").setProductName("Oracle").setProductMajorVersion(10);
		catalog.getSchemas().add(schema -> schema.setName("A"));
		catalog.getSchemas().add(schema -> schema.setName("B"));
		catalog.writeXml(command.getSchemaFile());
		command.setMigrationMethod(Method.LOGICAL_MIGRATION);
		command.run();
		assertEquals("REVIEW_REQUIRED", command.getReport().status());
		assertEquals(2, command.getReport().sources().size());
		assertEquals("DB", command.getReport().sources().getFirst().catalog());
	}

	@Test
	void rejectsInvalidInputsAndDoesNotOverwriteSource() throws Exception {
		final var command = command();
		command.setOutputFile(command.getSchemaFile());
		assertThrows(CommandException.class, command::run);
		assertTrue(Files.readString(command.getSchemaFile().toPath()).contains("schema"));
		command.setOutputFile(directory.resolve("out.json").toFile());
		command.setMigrationMethod(null);
		assertThrows(CommandException.class, command::run);
		command.setMigrationMethod(Method.LOGICAL_MIGRATION);
		command.setTargetVersion("19c");
		assertThrows(CommandException.class, command::run);
		command.setTargetVersion("26ai");
		new Table("T").writeXml(command.getSchemaFile());
		assertThrows(CommandException.class, command::run);
		Files.writeString(command.getSchemaFile().toPath(), "invalid XML");
		assertThrows(CommandException.class, command::run);
		assertNull(command.getReport());
		assertFalse(command.getOutputFile().exists());
	}

	@Test
	void recordsTargetCharsetAndUnknownColumnSemanticsWithoutChangingXml() throws Exception {
		final var command = command();
		final var schema = new Schema("日本語").setProductName("Oracle").setProductMajorVersion(10);
		final var table = new Table("T");
		table.getColumns().add(new Column("C").setDataType(DataType.VARCHAR).setLength(20));
		schema.getTables().add(table);
		schema.writeXml(command.getSchemaFile());
		final byte[] before = Files.readAllBytes(command.getSchemaFile().toPath());
		command.setMigrationMethod(Method.LOGICAL_MIGRATION);
		command.setTargetCharacterSet("AL32UTF8");
		command.run();
		assertEquals("AL32UTF8", command.getReport().targetCharacterSet());
		final String json = Files.readString(command.getOutputFile().toPath());
		assertTrue(json.contains("oracle.charset.source-unknown"));
		assertTrue(json.contains("oracle.charset.semantics-unknown"));
		assertTrue(json.contains("\"targetCharacterSet\""));
		assertArrayEquals(before, Files.readAllBytes(command.getSchemaFile().toPath()));
		command.setTargetCharacterSet("UTF8-32");
		assertThrows(CommandException.class, command::run);
		assertNull(command.getReport());
		assertEquals(json, Files.readString(command.getOutputFile().toPath()));
	}
}
