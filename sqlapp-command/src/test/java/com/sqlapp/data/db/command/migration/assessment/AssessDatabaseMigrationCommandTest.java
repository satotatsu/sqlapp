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
