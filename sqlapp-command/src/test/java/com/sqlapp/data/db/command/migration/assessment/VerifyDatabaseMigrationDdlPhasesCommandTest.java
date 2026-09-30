/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.exceptions.CommandException;

class VerifyDatabaseMigrationDdlPhasesCommandTest {
	@TempDir Path directory;

	@Test
	void verifiesCompleteArtifactsAndRejectsChangesAndInvalidManifests() throws Exception {
		final String provenance = "-- sqlapp sourceFingerprint: sha256:" + "1".repeat(64) + "\n"
				+ "-- sqlapp mappingFingerprint: sha256:" + "2".repeat(64) + "\n"
				+ "-- sqlapp target: oracle 19c\n";
		final var manifest = new StringBuilder("# manifest\n");
		for (final String section : List.of("phase-1", "phase-2", "phase-3", "phase-4", "appendix")) {
			final String name = section + ".sql";
			final String content = provenance + "-- sqlapp:" + section + ":begin\n-- content\n-- sqlapp:" + section + ":end\n";
			Files.writeString(directory.resolve(name), content);
			manifest.append(sha256(content)).append("  ").append(name).append('\n');
		}
		final Path report = directory.resolve("assessment.json");
		Files.writeString(report, """
				{"sourceFingerprint":"sha256:%s","mappingFingerprint":"sha256:%s","targetVersion":"19c","status":"BLOCKED",
				 "targetMapping":{"targetDatabase":"oracle"},
				 "assessment":{"inventory":[
				   {"type":"unresolvedAutoNumberStrategies","count":1},
				   {"type":"unmappedTables","count":2},
				   {"type":"unmappedColumnsInMappedTables","count":3},
				   {"type":"mappingSemanticDifferences","count":4}]}}
				""".formatted("1".repeat(64), "2".repeat(64)));
		manifest.insert("# manifest\n".length(), "# assessmentReport "
				+ AssessMigrationCommand.fingerprint(report.toFile()) + "\n");
		Files.writeString(directory.resolve("manifest.sha256"), manifest);
		final var command = new VerifyDatabaseMigrationDdlPhasesCommand();
		command.setDirectory(directory.toFile());
		command.setAssessmentReportFile(report.toFile());
		command.setExpectedManifestFingerprint("sha256:" + sha256(Files.readString(directory.resolve("manifest.sha256"))));
		command.setExpectedAssessmentReportFingerprint(AssessMigrationCommand.fingerprint(report.toFile()));
		command.setExpectedSourceFingerprint("sha256:" + "1".repeat(64));
		command.setExpectedMappingFingerprint("sha256:" + "2".repeat(64));
		command.setExpectedTargetDatabase("ORACLE");
		command.setExpectedTargetVersion("19C");
		final Path verificationReport = directory.resolveSibling("verification.json");
		command.setVerificationReportFile(verificationReport.toFile());
		assertDoesNotThrow(command::run);
		assertTrue(Files.readString(verificationReport).contains("\"status\" : \"VERIFIED\""));
		assertEquals("oracle", command.getVerificationReport().targetDatabase());
		assertEquals(2, command.getVerificationReport().formatVersion());
		assertEquals(1, command.getVerificationReport().unresolvedAutoNumberStrategies());
		assertEquals(2, command.getVerificationReport().unmappedTables());
		assertEquals(3, command.getVerificationReport().unmappedColumnsInMappedTables());
		assertEquals(4, command.getVerificationReport().mappingSemanticDifferences());
		assertEquals("BLOCKED", command.getVerificationReport().assessmentStatus());
		assertEquals(5, command.getVerificationReport().ddlFingerprints().size());
		assertTrue(Files.readString(verificationReport).contains("\"unresolvedAutoNumberStrategies\" : 1"));
		assertTrue(Files.readString(verificationReport).contains("\"unmappedTables\" : 2"));
		assertTrue(Files.readString(verificationReport).contains("\"unmappedColumnsInMappedTables\" : 3"));
		assertTrue(Files.readString(verificationReport).contains("\"mappingSemanticDifferences\" : 4"));
		assertTrue(Files.readString(verificationReport).contains("\"assessmentStatus\" : \"BLOCKED\""));
		assertEquals("sha256:" + sha256(Files.readString(directory.resolve("manifest.sha256"))),
				command.getVerificationReport().manifestFingerprint());
		final String successfulVerification = Files.readString(verificationReport);
		command.setFailOnUnresolvedAutoNumberStrategies(true);
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("unresolved Access AutoNumber"));
		assertEquals(successfulVerification, Files.readString(verificationReport));
		assertNull(command.getVerificationReport());
		command.setFailOnUnresolvedAutoNumberStrategies(false);
		command.setFailOnIncompleteMapping(true);
		assertTrue(assertThrows(CommandException.class, command::run).getMessage()
				.contains("2 unmapped tables and 3 unmapped columns"));
		assertEquals(successfulVerification, Files.readString(verificationReport));
		assertNull(command.getVerificationReport());
		command.setFailOnIncompleteMapping(false);
		command.setFailOnMappingSemanticDifferences(true);
		assertTrue(assertThrows(CommandException.class, command::run).getMessage()
				.contains("4 target mapping semantic differences"));
		assertEquals(successfulVerification, Files.readString(verificationReport));
		assertNull(command.getVerificationReport());
		command.setFailOnMappingSemanticDifferences(false);
		command.setFailOnAssessmentBlockers(true);
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("status is BLOCKED"));
		assertEquals(successfulVerification, Files.readString(verificationReport));
		assertNull(command.getVerificationReport());
		command.setFailOnAssessmentBlockers(false);
		command.setExpectedAssessmentReportFingerprint("sha256:" + "4".repeat(64));
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("expectedAssessmentReportFingerprint"));
		assertEquals(successfulVerification, Files.readString(verificationReport));
		assertNull(command.getVerificationReport());
		command.setExpectedAssessmentReportFingerprint(AssessMigrationCommand.fingerprint(report.toFile()));
		command.setExpectedManifestFingerprint("sha256:" + "5".repeat(64));
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("expectedManifestFingerprint"));
		command.setExpectedManifestFingerprint("sha256:" + sha256(Files.readString(directory.resolve("manifest.sha256"))));
		command.setExpectedSourceFingerprint("sha256:" + "3".repeat(64));
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("expectedSourceFingerprint"));
		command.setExpectedSourceFingerprint("invalid");
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("lowercase SHA-256"));
		command.setExpectedSourceFingerprint("sha256:" + "1".repeat(64));
		Files.writeString(report, Files.readString(report).replace("19c", "21c"));
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("assessmentReportFile"));
		Files.writeString(report, Files.readString(report).replace("21c", "19c"));

		Files.writeString(directory.resolve("phase-2.sql"), "-- changed\n", StandardCharsets.UTF_8);
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("checksum mismatch"));
		Files.writeString(directory.resolve("phase-2.sql"), provenance
				+ "-- sqlapp:phase-2:begin\n-- content\n-- sqlapp:phase-2:end\n");
		command.setExpectedManifestFingerprint(null);
		Files.writeString(directory.resolve("manifest.sha256"), manifest + "bad entry\n");
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("Invalid manifest"));
		Files.writeString(directory.resolve("manifest.sha256"), manifest.toString()
				.replaceFirst("(?m)^# assessmentReport .+\\R", ""));
		command.setAssessmentReportFile(null);
		command.setExpectedAssessmentReportFingerprint(null);
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("assessmentReport fingerprint"));
	}

	@Test
	void rejectsVerificationReportThatWouldOverwriteAssessmentReport() throws Exception {
		final var report = directory.resolveSibling("assessment.json");
		Files.writeString(report, "{}");
		final var command = new VerifyDatabaseMigrationDdlPhasesCommand();
		command.setDirectory(directory.toFile());
		command.setAssessmentReportFile(report.toFile());
		command.setVerificationReportFile(report.toFile());
		assertTrue(assertThrows(CommandException.class, command::run).getMessage()
				.contains("must not overwrite assessmentReportFile"));
		command.setAssessmentReportFile(null);
		command.setVerificationReportFile(null);
		command.setFailOnUnresolvedAutoNumberStrategies(true);
		assertTrue(assertThrows(CommandException.class, command::run).getMessage()
				.contains("requires assessmentReportFile"));
		command.setFailOnUnresolvedAutoNumberStrategies(false);
		command.setFailOnIncompleteMapping(true);
		assertTrue(assertThrows(CommandException.class, command::run).getMessage()
				.contains("failOnIncompleteMapping requires assessmentReportFile"));
		command.setFailOnIncompleteMapping(false);
		command.setFailOnMappingSemanticDifferences(true);
		assertTrue(assertThrows(CommandException.class, command::run).getMessage()
				.contains("failOnMappingSemanticDifferences requires assessmentReportFile"));
		command.setFailOnMappingSemanticDifferences(false);
		command.setFailOnAssessmentBlockers(true);
		assertTrue(assertThrows(CommandException.class, command::run).getMessage()
				.contains("failOnAssessmentBlockers requires assessmentReportFile"));
	}

	@Test
	void verifiesOlderAssessmentReportWithoutAutoNumberInventory() throws Exception {
		final String provenance = "-- sqlapp sourceFingerprint: sha256:" + "1".repeat(64) + "\n"
				+ "-- sqlapp mappingFingerprint: sha256:" + "2".repeat(64) + "\n"
				+ "-- sqlapp target: oracle 19c\n";
		final Path report = directory.resolveSibling("older-assessment.json");
		Files.writeString(report, """
				{"sourceFingerprint":"sha256:%s","mappingFingerprint":"sha256:%s","targetVersion":"19c",
				 "targetMapping":{"targetDatabase":"oracle"}}
				""".formatted("1".repeat(64), "2".repeat(64)));
		writeArtifact(provenance, AssessMigrationCommand.fingerprint(report.toFile()));
		final var command = new VerifyDatabaseMigrationDdlPhasesCommand();
		command.setDirectory(directory.toFile());
		command.setAssessmentReportFile(report.toFile());
		assertDoesNotThrow(command::run);
		assertEquals(2, command.getVerificationReport().formatVersion());
		assertNull(command.getVerificationReport().unresolvedAutoNumberStrategies());
		assertNull(command.getVerificationReport().unmappedTables());
		assertNull(command.getVerificationReport().unmappedColumnsInMappedTables());
		assertNull(command.getVerificationReport().mappingSemanticDifferences());
		assertNull(command.getVerificationReport().assessmentStatus());
	}

	@Test
	void rejectsSqlFilesOutsideTheManifest() throws Exception {
		final String provenance = "-- sqlapp sourceFingerprint: sha256:" + "1".repeat(64) + "\n"
				+ "-- sqlapp mappingFingerprint: sha256:" + "2".repeat(64) + "\n"
				+ "-- sqlapp target: oracle 19c\n";
		writeArtifact(provenance);
		Files.writeString(directory.resolve("old-phase.SQL"), "DROP TABLE customer;\n");
		final var command = new VerifyDatabaseMigrationDdlPhasesCommand();
		command.setDirectory(directory.toFile());
		final var error = assertThrows(CommandException.class, command::run);
		assertTrue(error.getMessage().contains("unverified SQL files"));
		assertTrue(error.getMessage().contains("old-phase.SQL"));
	}

	@Test
	void rejectsDuplicateAndMalformedProvenanceHeaders() throws Exception {
		final String valid = "-- sqlapp sourceFingerprint: sha256:" + "1".repeat(64) + "\n"
				+ "-- sqlapp mappingFingerprint: sha256:" + "2".repeat(64) + "\n"
				+ "-- sqlapp target: oracle 19c\n";
		writeArtifact(valid + "-- sqlapp sourceFingerprint: sha256:" + "3".repeat(64) + "\n");
		final var command = new VerifyDatabaseMigrationDdlPhasesCommand();
		command.setDirectory(directory.toFile());
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("exactly one sourceFingerprint"));

		writeArtifact(valid.replace("sha256:" + "1".repeat(64), "SHA256:" + "1".repeat(64)));
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("lowercase SHA-256"));

		writeArtifact(valid + "DROP TABLE unexpected;\n");
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("three canonical header lines"));
	}

	private void writeArtifact(final String provenance) throws Exception {
		writeArtifact(provenance, "sha256:" + "a".repeat(64));
	}

	private void writeArtifact(final String provenance, final String assessmentReportFingerprint) throws Exception {
		final var manifest = new StringBuilder("# manifest\n# assessmentReport ")
				.append(assessmentReportFingerprint).append('\n');
		for (final String section : List.of("phase-1", "phase-2", "phase-3", "phase-4", "appendix")) {
			final String name = section + ".sql";
			final String content = provenance + "-- sqlapp:" + section + ":begin\n-- content\n-- sqlapp:" + section + ":end\n";
			Files.writeString(directory.resolve(name), content);
			manifest.append(sha256(content)).append("  ").append(name).append('\n');
		}
		Files.writeString(directory.resolve("manifest.sha256"), manifest);
	}

	private static String sha256(final String value) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
	}
}
