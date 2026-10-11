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
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.JsonConverter;

import lombok.Getter;
import lombok.Setter;

/**
 * Verifies a phase-separated database migration DDL artifact without database
 * access.
 */
@Getter
@Setter
public class VerifyDatabaseMigrationDdlPhasesCommand extends AbstractCommand {
	private static final List<String> FILES = List.of("phase-1.sql", "phase-2.sql", "phase-3.sql", "phase-4.sql",
			"appendix.sql");
	private static final Pattern ENTRY = Pattern.compile("([0-9a-f]{64})  (phase-[1-4]\\.sql|appendix\\.sql)");
	private static final Pattern FINGERPRINT = Pattern.compile("sha256:[0-9a-f]{64}");
	private static final Pattern SOURCE_HEADER = Pattern.compile("(?m)^-- sqlapp sourceFingerprint: (\\S+)$");
	private static final Pattern MAPPING_HEADER = Pattern.compile("(?m)^-- sqlapp mappingFingerprint: (\\S+)$");
	private static final Pattern TARGET_HEADER = Pattern.compile("(?m)^-- sqlapp target: (\\S+) (\\S+)$");
	private File directory;
	private File assessmentReportFile;
	private File verificationReportFile;
	private VerificationReport verificationReport;
	private String expectedManifestFingerprint;
	private String expectedAssessmentReportFingerprint;
	private String expectedSourceFingerprint;
	private String expectedMappingFingerprint;
	private String expectedTargetDatabase;
	private String expectedTargetVersion;
	private boolean failOnUnresolvedAutoNumberStrategies;
	private boolean failOnIncompleteMapping;
	private boolean failOnMappingSemanticDifferences;
	private boolean failOnAssessmentBlockers;
	private boolean requireDeploymentReady;
	private boolean requireDataScan;
	private boolean requireRelationshipsCollected;
	private boolean requireApprovedFingerprints;

	@Override
	protected void doRun() {
		verificationReport = null;
		if (directory == null || !directory.isDirectory()) {
			throw new CommandException("directory must be an existing DDL phase output directory");
		}
		if (assessmentReportFile != null && !assessmentReportFile.isFile()) {
			throw new CommandException(
					"assessmentReportFile must be an existing database migration assessment JSON file");
		}
		if (verificationReportFile != null) {
			final var phaseDirectory = directory.toPath().toAbsolutePath().normalize();
			final var reportPath = verificationReportFile.toPath().toAbsolutePath().normalize();
			if (reportPath.startsWith(phaseDirectory)) {
				throw new CommandException("verificationReportFile must be outside directory");
			}
			if (assessmentReportFile != null
					&& reportPath.equals(assessmentReportFile.toPath().toAbsolutePath().normalize())) {
				throw new CommandException("verificationReportFile must not overwrite assessmentReportFile");
			}
		}
		final String assessmentReportPolicy = assessmentReportPolicy();
		if (assessmentReportPolicy != null && assessmentReportFile == null) {
			throw new CommandException(assessmentReportPolicy + " requires assessmentReportFile");
		}
		if (expectedAssessmentReportFingerprint != null && assessmentReportFile == null) {
			throw new CommandException("expectedAssessmentReportFingerprint requires assessmentReportFile");
		}
		if (requireDeploymentReady || requireApprovedFingerprints) {
			if (expectedAssessmentReportFingerprint == null || expectedManifestFingerprint == null) {
				throw new CommandException(
						(requireDeploymentReady ? "requireDeploymentReady" : "requireApprovedFingerprints")
								+ " requires expectedAssessmentReportFingerprint and expectedManifestFingerprint");
			}
		}
		validateFingerprint("expectedSourceFingerprint", expectedSourceFingerprint);
		validateFingerprint("expectedMappingFingerprint", expectedMappingFingerprint);
		validateFingerprint("expectedManifestFingerprint", expectedManifestFingerprint);
		validateFingerprint("expectedAssessmentReportFingerprint", expectedAssessmentReportFingerprint);
		if (expectedTargetDatabase != null && expectedTargetDatabase.isBlank()) {
			throw new CommandException("expectedTargetDatabase must not be blank");
		}
		if (expectedTargetVersion != null && expectedTargetVersion.isBlank()) {
			throw new CommandException("expectedTargetVersion must not be blank");
		}
		try {
			final var manifestPath = directory.toPath().resolve("manifest.sha256");
			if (!Files.isRegularFile(manifestPath)) {
				throw new CommandException("manifest.sha256 is missing: " + directory);
			}
			final byte[] manifestBytes = Files.readAllBytes(manifestPath);
			final String manifestFingerprint = "sha256:" + sha256(manifestBytes);
			if (expectedManifestFingerprint != null && !expectedManifestFingerprint.equals(manifestFingerprint)) {
				throw new CommandException("manifest.sha256 does not match expectedManifestFingerprint");
			}
			final var entries = new LinkedHashMap<String, String>();
			String manifestReportFingerprint = null;
			for (final String line : new String(manifestBytes, StandardCharsets.UTF_8).lines().toList()) {
				if (line.startsWith("# assessmentReport ")) {
					if (manifestReportFingerprint != null) {
						throw new CommandException("Duplicate assessmentReport fingerprint in manifest.sha256");
					}
					manifestReportFingerprint = line.substring("# assessmentReport ".length());
					validateFingerprint("manifest assessmentReport fingerprint", manifestReportFingerprint);
					continue;
				}
				if (line.isBlank() || line.startsWith("#")) {
					continue;
				}
				final var matcher = ENTRY.matcher(line);
				if (!matcher.matches()) {
					throw new CommandException("Invalid manifest.sha256 entry: " + line);
				}
				if (entries.putIfAbsent(matcher.group(2), matcher.group(1)) != null) {
					throw new CommandException("Duplicate manifest.sha256 entry: " + matcher.group(2));
				}
			}
			if (!entries.keySet().equals(Set.copyOf(FILES))) {
				throw new CommandException("manifest.sha256 must contain exactly the five generated DDL files");
			}
			if (manifestReportFingerprint == null) {
				throw new CommandException("manifest.sha256 must contain exactly one assessmentReport fingerprint");
			}
			try (var paths = Files.list(directory.toPath())) {
				final var unexpectedSql = paths.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
						.filter(name -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".sql"))
						.filter(name -> !FILES.contains(name)).sorted().toList();
				if (!unexpectedSql.isEmpty()) {
					throw new CommandException(
							"DDL phase directory contains unverified SQL files: " + String.join(", ", unexpectedSql));
				}
			}
			String provenance = null;
			for (final String name : FILES) {
				final var file = directory.toPath().resolve(name);
				if (!Files.isRegularFile(file)) {
					throw new CommandException("DDL phase file is missing: " + name);
				}
				final byte[] bytes = Files.readAllBytes(file);
				if (!entries.get(name).equals(sha256(bytes))) {
					throw new CommandException("DDL phase checksum mismatch: " + name);
				}
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
				if (provenance == null) {
					provenance = currentProvenance;
				} else if (!provenance.equals(currentProvenance)) {
					throw new CommandException("DDL phase provenance headers do not match: " + name);
				}
			}
			final ProvenanceIdentity identity = parseProvenance(provenance);
			verifyExpectedProvenance(identity);
			AssessmentInventory assessmentInventory = AssessmentInventory.EMPTY;
			if (assessmentReportFile != null) {
				if (!manifestReportFingerprint.equals(AssessMigrationCommand.fingerprint(assessmentReportFile))) {
					throw new CommandException("assessmentReportFile does not match manifest.sha256");
				}
				assessmentInventory = verifyAssessmentReport(identity);
			}
			verificationReport = new VerificationReport(2, "VERIFIED", manifestFingerprint, manifestReportFingerprint,
					identity.sourceFingerprint(), identity.mappingFingerprint(), identity.targetDatabase(),
					identity.targetVersion(), assessmentInventory.unresolvedAutoNumberStrategies(),
					assessmentInventory.unmappedTables(), assessmentInventory.unmappedColumnsInMappedTables(),
					assessmentInventory.mappingSemanticDifferences(), assessmentInventory.assessmentStatus(),
					assessmentInventory.dataScanned(), assessmentInventory.relationshipsCollected(),
					assessmentInventory.assessmentFormatVersion(), assessmentInventory.sourceProduct(),
					assessmentInventory.targetProduct(), verificationPolicies(),
					java.util.Collections.unmodifiableMap(new LinkedHashMap<>(entries)));
			if (verificationReportFile != null) {
				final var converter = new JsonConverter();
				converter.setIndentOutput(true);
				AtomicMigrationFile.write(verificationReportFile.toPath().toAbsolutePath().normalize(),
						temporary -> converter.writeJsonValue(temporary.toFile(), verificationReport));
			}
		} catch (final CommandException e) {
			throw e;
		} catch (final Exception e) {
			throw new CommandException("DDL phase verification failed: " + e.getMessage(), e);
		}
		info("Database migration DDL phases verified: ", directory.getAbsolutePath());
	}

	private String assessmentReportPolicy() {
		if (requireDeploymentReady) {
			return "requireDeploymentReady";
		}
		if (requireApprovedFingerprints) {
			return "requireApprovedFingerprints";
		}
		if (requireDataScan) {
			return "requireDataScan";
		}
		if (requireRelationshipsCollected) {
			return "requireRelationshipsCollected";
		}
		if (failOnUnresolvedAutoNumberStrategies) {
			return "failOnUnresolvedAutoNumberStrategies";
		}
		if (failOnIncompleteMapping) {
			return "failOnIncompleteMapping";
		}
		if (failOnMappingSemanticDifferences) {
			return "failOnMappingSemanticDifferences";
		}
		if (failOnAssessmentBlockers) {
			return "failOnAssessmentBlockers";
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private AssessmentInventory verifyAssessmentReport(final ProvenanceIdentity identity) {
		if (expectedAssessmentReportFingerprint != null) {
			try {
				if (!expectedAssessmentReportFingerprint
						.equals(AssessMigrationCommand.fingerprint(assessmentReportFile))) {
					throw new CommandException(
							"assessmentReportFile does not match expectedAssessmentReportFingerprint");
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
		final String sourceProduct = report.get("sourceProduct") instanceof String product && !product.isBlank()
				? product
				: null;
		final String targetProduct = report.get("targetProduct") instanceof String product && !product.isBlank()
				? product
				: null;
		if (!(report.get("targetMapping") instanceof java.util.Map<?, ?> targetMapping)) {
			throw new CommandException("assessmentReportFile must contain targetMapping");
		}
		final String database = stringValue(targetMapping.get("targetDatabase"), "targetMapping.targetDatabase");
		if (requireDeploymentReady) {
			if (!"access".equalsIgnoreCase(sourceProduct)) {
				throw new CommandException(
						"assessmentReportFile sourceProduct must be access for requireDeploymentReady");
			}
			if (targetProduct == null || !targetProduct.equalsIgnoreCase(database)) {
				throw new CommandException(
						"assessmentReportFile targetProduct must match targetMapping.targetDatabase");
			}
		}
		final String assessmentStatus = report.get("status") instanceof String status && !status.isBlank() ? status
				: null;
		final Integer assessmentFormatVersion = integerValue(report.get("formatVersion"));
		if (requireDeploymentReady && !Integer.valueOf(3).equals(assessmentFormatVersion)) {
			throw new CommandException("assessmentReportFile formatVersion must be 3 for requireDeploymentReady");
		}
		final Boolean dataScanned = report.get("dataScanned") instanceof Boolean scanned ? scanned : null;
		final Boolean relationshipsCollected = report.get("relationshipsCollected") instanceof Boolean collected
				? collected
				: null;
		if (requireDeploymentReady || requireDataScan) {
			if (!Boolean.TRUE.equals(dataScanned)) {
				throw new CommandException(
						"assessmentReportFile must have dataScanned=true; rerun assessment with scanData enabled");
			}
			if (!(report.get("dataProfile") instanceof java.util.Map<?, ?>)) {
				throw new CommandException("assessmentReportFile must contain dataProfile when dataScanned=true");
			}
		}
		if (requireDeploymentReady || requireRelationshipsCollected) {
			if (!Boolean.TRUE.equals(relationshipsCollected)) {
				throw new CommandException("assessmentReportFile must have relationshipsCollected=true; "
						+ "rerun assessment with an Access source that exposes relationships");
			}
		}
		if (requireDeploymentReady || failOnAssessmentBlockers) {
			if (assessmentStatus == null) {
				throw new CommandException(
						"assessmentReportFile must contain status when blocker verification is enabled");
			}
			if ("BLOCKED".equalsIgnoreCase(assessmentStatus)) {
				throw new CommandException(
						"assessmentReportFile status is BLOCKED; resolve its migration blockers before deployment");
			}
			if (!"REVIEW_REQUIRED".equalsIgnoreCase(assessmentStatus)) {
				throw new CommandException("assessmentReportFile contains unsupported status: " + assessmentStatus);
			}
		}
		final Integer unresolved = inventoryCount(report, "unresolvedAutoNumberStrategies",
				requireDeploymentReady || failOnUnresolvedAutoNumberStrategies);
		final Integer unmappedTables = inventoryCount(report, "unmappedTables",
				requireDeploymentReady || failOnIncompleteMapping);
		final Integer unmappedColumns = inventoryCount(report, "unmappedColumnsInMappedTables",
				requireDeploymentReady || failOnIncompleteMapping);
		final Integer semanticDifferences = inventoryCount(report, "mappingSemanticDifferences",
				failOnMappingSemanticDifferences);
		if (requireDeploymentReady || failOnUnresolvedAutoNumberStrategies) {
			if (unresolved > 0) {
				throw new CommandException("assessmentReportFile contains " + unresolved
						+ " unresolved Access AutoNumber strategies; set each mapped identity to true or false");
			}
		}
		if (requireDeploymentReady || failOnIncompleteMapping) {
			if (unmappedTables > 0 || unmappedColumns > 0) {
				throw new CommandException(
						"assessmentReportFile contains an incomplete target mapping: " + unmappedTables
								+ " unmapped tables and " + unmappedColumns + " unmapped columns in mapped tables");
			}
		}
		if (failOnMappingSemanticDifferences && semanticDifferences > 0) {
			throw new CommandException("assessmentReportFile contains " + semanticDifferences
					+ " target mapping semantic differences requiring explicit review");
		}
		validateFingerprint("assessmentReportFile sourceFingerprint", source);
		validateFingerprint("assessmentReportFile mappingFingerprint", mapping);
		if (!identity.sourceFingerprint().equals(source) || !identity.mappingFingerprint().equals(mapping)
				|| !identity.targetDatabase().equalsIgnoreCase(database)
				|| !identity.targetVersion().equalsIgnoreCase(version)) {
			throw new CommandException("DDL phases do not match assessmentReportFile");
		}
		return new AssessmentInventory(unresolved, unmappedTables, unmappedColumns, semanticDifferences,
				assessmentStatus, dataScanned, relationshipsCollected, assessmentFormatVersion, sourceProduct,
				targetProduct);
	}

	private static Integer integerValue(final Object value) {
		if (!(value instanceof Number number) || number.intValue() < 0 || number.doubleValue() != number.intValue()) {
			return null;
		}
		return number.intValue();
	}

	private static Integer inventoryCount(final java.util.Map<?, ?> report, final String type, final boolean required) {
		if (!(report.get("assessment") instanceof java.util.Map<?, ?> assessment)
				|| !(assessment.get("inventory") instanceof java.util.List<?> inventory)) {
			if (required) {
				throw new CommandException("assessmentReportFile must contain assessment.inventory for " + type);
			}
			return null;
		}
		Integer result = null;
		for (final Object value : inventory) {
			if (!(value instanceof java.util.Map<?, ?> item) || !type.equals(item.get("type"))) {
				continue;
			}
			if (result != null) {
				throw new CommandException("assessmentReportFile contains duplicate inventory type: " + type);
			}
			if (!(item.get("count") instanceof Number count) || count.intValue() < 0
					|| count.doubleValue() != count.intValue()) {
				throw new CommandException("assessmentReportFile contains an invalid inventory count for " + type);
			}
			result = count.intValue();
		}
		if (result == null && required) {
			throw new CommandException("assessmentReportFile is missing inventory type: " + type);
		}
		return result;
	}

	private static String stringValue(final Object value, final String property) {
		if (!(value instanceof String text) || text.isBlank()) {
			throw new CommandException("assessmentReportFile must contain " + property);
		}
		return text;
	}

	private void verifyExpectedProvenance(final ProvenanceIdentity identity) {
		if (expectedSourceFingerprint != null && !identity.sourceFingerprint().equals(expectedSourceFingerprint)) {
			throw new CommandException("DDL phases do not match expectedSourceFingerprint");
		}
		if (expectedMappingFingerprint != null && !identity.mappingFingerprint().equals(expectedMappingFingerprint)) {
			throw new CommandException("DDL phases do not match expectedMappingFingerprint");
		}
		if (expectedTargetDatabase != null || expectedTargetVersion != null) {
			if (expectedTargetDatabase != null && !expectedTargetDatabase.equalsIgnoreCase(identity.targetDatabase())
					|| expectedTargetVersion != null
							&& !expectedTargetVersion.equalsIgnoreCase(identity.targetVersion())) {
				throw new CommandException("DDL phases do not match expected target database and version");
			}
		}
	}

	private static ProvenanceIdentity parseProvenance(final String provenance) {
		final String source = uniqueHeader(SOURCE_HEADER, provenance, "sourceFingerprint");
		final String mapping = uniqueHeader(MAPPING_HEADER, provenance, "mappingFingerprint");
		validateFingerprint("DDL phase sourceFingerprint", source);
		validateFingerprint("DDL phase mappingFingerprint", mapping);
		final var target = TARGET_HEADER.matcher(provenance);
		if (!target.find()) {
			throw new CommandException("DDL phase provenance header must contain exactly one target");
		}
		final String database = target.group(1);
		final String version = target.group(2);
		if (target.find()) {
			throw new CommandException("DDL phase provenance header must contain exactly one target");
		}
		final String canonical = "-- sqlapp sourceFingerprint: " + source + "\n" + "-- sqlapp mappingFingerprint: "
				+ mapping + "\n" + "-- sqlapp target: " + database + " " + version + "\n";
		if (!canonical.equals(provenance)) {
			throw new CommandException(
					"DDL phase provenance header must contain only the three canonical header lines");
		}
		return new ProvenanceIdentity(source, mapping, database, version);
	}

	private static String uniqueHeader(final Pattern pattern, final String provenance, final String property) {
		final var matcher = pattern.matcher(provenance);
		if (!matcher.find()) {
			throw new CommandException("DDL phase provenance header must contain exactly one " + property);
		}
		final String value = matcher.group(1);
		if (matcher.find()) {
			throw new CommandException("DDL phase provenance header must contain exactly one " + property);
		}
		return value;
	}

	private record ProvenanceIdentity(String sourceFingerprint, String mappingFingerprint, String targetDatabase,
			String targetVersion) {
	}

	private record AssessmentInventory(Integer unresolvedAutoNumberStrategies, Integer unmappedTables,
			Integer unmappedColumnsInMappedTables, Integer mappingSemanticDifferences, String assessmentStatus,
			Boolean dataScanned, Boolean relationshipsCollected, Integer assessmentFormatVersion, String sourceProduct,
			String targetProduct) {
		private static final AssessmentInventory EMPTY = new AssessmentInventory(null, null, null, null, null, null,
				null, null, null, null);
	}

	private java.util.List<String> verificationPolicies() {
		final var policies = new java.util.ArrayList<String>();
		if (requireDeploymentReady) {
			policies.add("DEPLOYMENT_READY");
		}
		if (requireDeploymentReady || failOnAssessmentBlockers) {
			policies.add("ASSESSMENT_BLOCKERS");
		}
		if (requireDeploymentReady || failOnIncompleteMapping) {
			policies.add("INCOMPLETE_MAPPING");
		}
		if (requireDeploymentReady || failOnUnresolvedAutoNumberStrategies) {
			policies.add("UNRESOLVED_AUTONUMBER");
		}
		if (failOnMappingSemanticDifferences) {
			policies.add("MAPPING_SEMANTIC_DIFFERENCES");
		}
		if (requireDeploymentReady || requireDataScan) {
			policies.add("DATA_SCAN");
		}
		if (requireDeploymentReady || requireRelationshipsCollected) {
			policies.add("RELATIONSHIPS_COLLECTED");
		}
		if (requireDeploymentReady || requireApprovedFingerprints) {
			policies.add("APPROVED_FINGERPRINTS");
		}
		return java.util.List.copyOf(policies);
	}

	/**
	 * Verification evidence. {@code VERIFIED} means that every configured check
	 * passed; approval is asserted only when {@code APPROVED_FINGERPRINTS} is
	 * present in {@code verificationPolicies}, while {@code DEPLOYMENT_READY}
	 * identifies the composite deployment gate.
	 */
	public record VerificationReport(int formatVersion, String status, String manifestFingerprint,
			String assessmentReportFingerprint, String sourceFingerprint, String mappingFingerprint,
			String targetDatabase, String targetVersion,
			@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Integer unresolvedAutoNumberStrategies,
			@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Integer unmappedTables,
			@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Integer unmappedColumnsInMappedTables,
			@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Integer mappingSemanticDifferences,
			@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String assessmentStatus,
			@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Boolean dataScanned,
			@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Boolean relationshipsCollected,
			@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Integer assessmentFormatVersion,
			@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String sourceProduct,
			@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String targetProduct,
			java.util.List<String> verificationPolicies, java.util.Map<String, String> ddlFingerprints) {
		public VerificationReport(final int formatVersion, final String status, final String manifestFingerprint,
				final String assessmentReportFingerprint, final String sourceFingerprint,
				final String mappingFingerprint, final String targetDatabase, final String targetVersion,
				final Integer unresolvedAutoNumberStrategies, final java.util.Map<String, String> ddlFingerprints) {
			this(formatVersion, status, manifestFingerprint, assessmentReportFingerprint, sourceFingerprint,
					mappingFingerprint, targetDatabase, targetVersion, unresolvedAutoNumberStrategies, null, null, null,
					null, null, null, null, null, null, java.util.List.of(), ddlFingerprints);
		}

		public VerificationReport(final int formatVersion, final String status, final String manifestFingerprint,
				final String assessmentReportFingerprint, final String sourceFingerprint,
				final String mappingFingerprint, final String targetDatabase, final String targetVersion,
				final java.util.Map<String, String> ddlFingerprints) {
			this(formatVersion, status, manifestFingerprint, assessmentReportFingerprint, sourceFingerprint,
					mappingFingerprint, targetDatabase, targetVersion, null, null, null, null, null, null, null, null,
					null, null, java.util.List.of(), ddlFingerprints);
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
