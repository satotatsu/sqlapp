/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.JsonConverter;

import lombok.Getter;
import lombok.Setter;

/** Verifies a phase-separated database migration DDL artifact without database access. */
@Getter
@Setter
public class VerifyDatabaseMigrationDdlPhasesCommand extends AbstractCommand {
	private static final List<String> FILES = List.of(
			"phase-1.sql", "phase-2.sql", "phase-3.sql", "phase-4.sql", "appendix.sql");
	private static final Pattern ENTRY = Pattern.compile("([0-9a-f]{64})  (phase-[1-4]\\.sql|appendix\\.sql)");
	private static final Pattern FINGERPRINT = Pattern.compile("sha256:[0-9a-f]{64}");
	private File directory;
	private File assessmentReportFile;
	private String expectedAssessmentReportFingerprint;
	private String expectedSourceFingerprint;
	private String expectedMappingFingerprint;
	private String expectedTargetDatabase;
	private String expectedTargetVersion;

	@Override
	protected void doRun() {
		if (directory == null || !directory.isDirectory()) {
			throw new CommandException("directory must be an existing DDL phase output directory");
		}
		if (assessmentReportFile != null && !assessmentReportFile.isFile()) {
			throw new CommandException("assessmentReportFile must be an existing database migration assessment JSON file");
		}
		if (expectedAssessmentReportFingerprint != null && assessmentReportFile == null) {
			throw new CommandException("expectedAssessmentReportFingerprint requires assessmentReportFile");
		}
		validateFingerprint("expectedSourceFingerprint", expectedSourceFingerprint);
		validateFingerprint("expectedMappingFingerprint", expectedMappingFingerprint);
		validateFingerprint("expectedAssessmentReportFingerprint", expectedAssessmentReportFingerprint);
		if (expectedTargetDatabase != null && expectedTargetDatabase.isBlank()) {
			throw new CommandException("expectedTargetDatabase must not be blank");
		}
		if (expectedTargetVersion != null && expectedTargetVersion.isBlank()) {
			throw new CommandException("expectedTargetVersion must not be blank");
		}
		try {
			final var manifestPath = directory.toPath().resolve("manifest.sha256");
			if (!Files.isRegularFile(manifestPath)) { throw new CommandException("manifest.sha256 is missing: " + directory); }
			final var entries = new LinkedHashMap<String, String>();
			String manifestReportFingerprint = null;
			for (final String line : Files.readAllLines(manifestPath, StandardCharsets.UTF_8)) {
				if (line.startsWith("# assessmentReport ")) {
					if (manifestReportFingerprint != null) { throw new CommandException("Duplicate assessmentReport fingerprint in manifest.sha256"); }
					manifestReportFingerprint = line.substring("# assessmentReport ".length());
					validateFingerprint("manifest assessmentReport fingerprint", manifestReportFingerprint);
					continue;
				}
				if (line.isBlank() || line.startsWith("#")) { continue; }
				final var matcher = ENTRY.matcher(line);
				if (!matcher.matches()) { throw new CommandException("Invalid manifest.sha256 entry: " + line); }
				if (entries.putIfAbsent(matcher.group(2), matcher.group(1)) != null) {
					throw new CommandException("Duplicate manifest.sha256 entry: " + matcher.group(2));
				}
			}
			if (!entries.keySet().equals(Set.copyOf(FILES))) {
				throw new CommandException("manifest.sha256 must contain exactly the five generated DDL files");
			}
			String provenance = null;
			for (final String name : FILES) {
				final var file = directory.toPath().resolve(name);
				if (!Files.isRegularFile(file)) { throw new CommandException("DDL phase file is missing: " + name); }
				final byte[] bytes = Files.readAllBytes(file);
				if (!entries.get(name).equals(sha256(bytes))) { throw new CommandException("DDL phase checksum mismatch: " + name); }
				final String content = new String(bytes, StandardCharsets.UTF_8);
				final String section = name.equals("appendix.sql") ? "appendix" : name.substring(0, name.length() - 4);
				final String begin = "-- sqlapp:" + section + ":begin";
				final String end = "-- sqlapp:" + section + ":end";
				final int marker = content.indexOf(begin);
				if (marker < 0 || marker != content.lastIndexOf(begin) || content.indexOf(end, marker) < 0
						|| content.indexOf(end, marker) != content.lastIndexOf(end) || !content.endsWith(end + "\n")) {
					throw new CommandException("DDL phase markers are invalid: " + name);
				}
				for (final String other : List.of("phase-1", "phase-2", "phase-3", "phase-4", "appendix")) {
					if (!other.equals(section) && content.contains("-- sqlapp:" + other + ":begin")) {
						throw new CommandException("DDL phase contains another section marker: " + name);
					}
				}
				final String currentProvenance = content.substring(0, marker);
				if (provenance == null) { provenance = currentProvenance; }
				else if (!provenance.equals(currentProvenance)) {
					throw new CommandException("DDL phase provenance headers do not match: " + name);
				}
			}
			if (provenance == null || !provenance.contains("-- sqlapp sourceFingerprint: sha256:")
					|| !provenance.contains("-- sqlapp mappingFingerprint: sha256:")
					|| !provenance.contains("-- sqlapp target: ")) {
				throw new CommandException("DDL phase provenance header is incomplete");
			}
			verifyExpectedProvenance(provenance);
			if (assessmentReportFile != null) {
				if (manifestReportFingerprint == null) {
					throw new CommandException("manifest.sha256 does not bind an assessmentReportFile");
				}
				if (!manifestReportFingerprint.equals(AssessMigrationCommand.fingerprint(assessmentReportFile))) {
					throw new CommandException("assessmentReportFile does not match manifest.sha256");
				}
				verifyAssessmentReport(provenance);
			}
		} catch (final CommandException e) {
			throw e;
		} catch (final Exception e) {
			throw new CommandException("DDL phase verification failed: " + e.getMessage(), e);
		}
		info("Database migration DDL phases verified: ", directory.getAbsolutePath());
	}

	@SuppressWarnings("unchecked")
	private void verifyAssessmentReport(final String provenance) {
		if (expectedAssessmentReportFingerprint != null) {
			try {
				if (!expectedAssessmentReportFingerprint.equals(AssessMigrationCommand.fingerprint(assessmentReportFile))) {
					throw new CommandException("assessmentReportFile does not match expectedAssessmentReportFingerprint");
				}
			} catch (final CommandException e) {
				throw e;
			} catch (final Exception e) {
				throw new CommandException("Could not fingerprint assessmentReportFile: " + e.getMessage(), e);
			}
		}
		final Object value = new JsonConverter().fromJsonString(assessmentReportFile, java.util.Map.class);
		if (!(value instanceof java.util.Map<?, ?> report)) {
			throw new CommandException("assessmentReportFile must contain a JSON object");
		}
		final String source = stringValue(report.get("sourceFingerprint"), "sourceFingerprint");
		final String mapping = stringValue(report.get("mappingFingerprint"), "mappingFingerprint");
		final String version = stringValue(report.get("targetVersion"), "targetVersion");
		if (!(report.get("targetMapping") instanceof java.util.Map<?, ?> targetMapping)) {
			throw new CommandException("assessmentReportFile must contain targetMapping");
		}
		final String database = stringValue(targetMapping.get("targetDatabase"), "targetMapping.targetDatabase");
		validateFingerprint("assessmentReportFile sourceFingerprint", source);
		validateFingerprint("assessmentReportFile mappingFingerprint", mapping);
		if (!provenance.contains("-- sqlapp sourceFingerprint: " + source + "\n")
				|| !provenance.contains("-- sqlapp mappingFingerprint: " + mapping + "\n")
				|| !Pattern.compile("(?mi)^-- sqlapp target: " + Pattern.quote(database) + " "
						+ Pattern.quote(version) + "$").matcher(provenance).find()) {
			throw new CommandException("DDL phases do not match assessmentReportFile");
		}
	}

	private static String stringValue(final Object value, final String property) {
		if (!(value instanceof String text) || text.isBlank()) {
			throw new CommandException("assessmentReportFile must contain " + property);
		}
		return text;
	}

	private void verifyExpectedProvenance(final String provenance) {
		if (expectedSourceFingerprint != null
				&& !provenance.contains("-- sqlapp sourceFingerprint: " + expectedSourceFingerprint + "\n")) {
			throw new CommandException("DDL phases do not match expectedSourceFingerprint");
		}
		if (expectedMappingFingerprint != null
				&& !provenance.contains("-- sqlapp mappingFingerprint: " + expectedMappingFingerprint + "\n")) {
			throw new CommandException("DDL phases do not match expectedMappingFingerprint");
		}
		if (expectedTargetDatabase != null || expectedTargetVersion != null) {
			final var matcher = Pattern.compile("(?m)^-- sqlapp target: (\\S+) (\\S+)$").matcher(provenance);
			if (!matcher.find()
					|| expectedTargetDatabase != null && !expectedTargetDatabase.equalsIgnoreCase(matcher.group(1))
					|| expectedTargetVersion != null && !expectedTargetVersion.equalsIgnoreCase(matcher.group(2))) {
				throw new CommandException("DDL phases do not match expected target database and version");
			}
		}
	}

	private static void validateFingerprint(final String property, final String fingerprint) {
		if (fingerprint != null && !FINGERPRINT.matcher(fingerprint).matches()) {
			throw new CommandException(property + " must be a lowercase SHA-256 value");
		}
	}

	private static String sha256(final byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}
}
