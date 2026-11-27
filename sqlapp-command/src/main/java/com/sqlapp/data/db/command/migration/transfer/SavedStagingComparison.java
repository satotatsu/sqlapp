/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO;
import com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile;
import com.sqlapp.data.db.command.migration.schema.MigrationPlan.DatabaseIdentity;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskVerificationResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobVerificationResult;
import com.sqlapp.jdbc.bulk.BulkMigrationUnorderedVerifier;
import com.sqlapp.jdbc.bulk.BulkMigrationVerificationChunk;
import com.sqlapp.jdbc.bulk.BulkMigrationVerificationResult;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads only comparison evidence. No path or SQL from a JSON report is
 * executed.
 */
final class SavedStagingComparison {
	private static final long MAX_REPORT_BYTES = 32L * 1024 * 1024;
	private static final JsonMapper MAPPER = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
			.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

	record Evidence(PlanResolver plan, String jobFingerprint, String planFingerprint, DatabaseIdentity targetIdentity,
			List<TransferStagedReplacement.Target> targets, BulkMigrationJobVerificationResult verification,
			String resultFingerprint) {
		Evidence {
			targets = List.copyOf(targets);
		}
	}

	@FunctionalInterface
	interface PlanResolver {
		com.sqlapp.jdbc.bulk.BulkMigrationJobPlan resolve(java.sql.Connection connection) throws Exception;
	}

	private record Report(JsonNode document, String fingerprint) {
	}

	private SavedStagingComparison() {
	}

	static Evidence read(Path resultReport) {
		var resultFile = resultReport.toAbsolutePath().normalize();
		var directory = resultFile.getParent();
		try {
			var resultReportSnapshot = report(resultFile, "outcome");
			var result = resultReportSnapshot.document();
			var outcome = result.path("outcome");
			var staging = outcome.path("staging");
			if (!"COMMIT_UNCERTAIN".equals(staging.path("state").stringValue()))
				throw new TransferSchemaPreparation.ValidationException(
						"Select a transfer-result.json with an unconfirmed staged replacement.");
			if (resultFile.getFileName().toString().equals("import-result.json"))
				return readFileImport(directory, resultReportSnapshot, outcome, staging);
			var review = report(sibling(directory, "transfer-review.json"), "review").document().path("review");
			var reviewed = review.path("prepared");
			var executed = outcome.path("prepared");
			var job = new TransferJobConfigurationIO().read(sibling(directory, "transfer.yaml"));
			var bulkIO = new BulkMigrationJobConfigurationEditorIO();
			var bulk = bulkIO.read(sibling(directory, "data-job.yaml"));
			var schema = sibling(directory, "source-schema.xml");
			var bulkConfig = bulkIO.validate(bulk.text());
			var planFingerprint = text(reviewed, "planFingerprint");
			if (job.configuration()
					.effectiveTargetPreparation() != TransferJobConfiguration.TargetPreparation.STAGED_REPLACE
					|| !matchesArtifacts(reviewed, job.fingerprint(), bulk.fingerprint(), planFingerprint)
					|| !matchesArtifacts(executed, job.fingerprint(), bulk.fingerprint(), planFingerprint)
					|| !"source-schema.xml".equals(bulkConfig.getSchemaFile()))
				throw new IllegalArgumentException();
			checkSchema(schema, bulkConfig.getSchemaFingerprint());
			var targets = targets(review.path("staging").path("targets"));
			if (targets.isEmpty() || !targets.equals(targets(staging.path("targets"))))
				throw new IllegalArgumentException();
			var identity = review.path("targetIdentity");
			// Older results recorded the identity only in the sibling review.
			var resultIdentity = result.get("targetIdentity");
			if (resultIdentity != null && !identity.equals(resultIdentity))
				throw new IllegalArgumentException();
			var targetIdentity = new DatabaseIdentity(text(identity, "productName"), text(identity, "productVersion"),
					text(identity, "connectionFingerprint"));
			var verification = verification(outcome.path("verificationResult"), planFingerprint);
			return new Evidence(connection -> TransferStagedReplacement.resolve(bulk, connection), job.fingerprint(),
					planFingerprint, targetIdentity, targets, verification, resultReportSnapshot.fingerprint());
		} catch (TransferSchemaPreparation.ValidationException failure) {
			throw failure;
		} catch (Exception failure) {
			if (resultFile.getFileName().toString().equals("import-result.json"))
				throw new TransferSchemaPreparation.ValidationException(
						"The import result or captured package is missing or inconsistent. "
								+ "Select import-result.json from the import run folder and keep its captured manifest and package files together.");
			throw new TransferSchemaPreparation.ValidationException(
					"The saved result, review or captured plan is missing or inconsistent. "
							+ "Select the result from its original transfer folder, with transfer-review.json, transfer.yaml, data-job.yaml and source-schema.xml.");
		}
	}

	private static Evidence readFileImport(Path directory, Report snapshot, JsonNode outcome, JsonNode staging)
			throws Exception {
		var report = snapshot.document();
		var fingerprint = text(outcome, "manifestFingerprint");
		var planFingerprint = text(report, "copyPlanFingerprint");
		var names = targets(staging.path("targets"));
		var tasks = new ArrayList<BulkMigrationJobTaskVerificationResult>();
		var verification = outcome.path("verification");
		for (var task : array(report.path("taskIds"))) {
			if (!task.isString())
				throw new IllegalArgumentException();
			tasks.add(readVerification(task.stringValue(), verification.path(task.stringValue())));
		}
		var recorded = new BulkMigrationJobVerificationResult(planFingerprint, tasks);
		if (names.isEmpty() || names.size() != tasks.size() || !recorded.isMatch())
			throw new IllegalArgumentException();
		var identity = report.path("targetIdentity");
		var target = new DatabaseIdentity(text(identity, "productName"), text(identity, "productVersion"),
				text(identity, "connectionFingerprint"));
		var plan = TransferFileImportService.comparisonPlan(sibling(directory, "manifest.json"), fingerprint);
		return new Evidence(plan, fingerprint, planFingerprint, target, names, recorded, snapshot.fingerprint());
	}

	private static boolean matchesArtifacts(JsonNode prepared, String job, String bulk, String plan) {
		return job.equals(prepared.path("job").path("fingerprint").stringValue())
				&& bulk.equals(prepared.path("dataJob").path("fingerprint").stringValue())
				&& plan.equals(prepared.path("planFingerprint").stringValue());
	}

	private static Path sibling(Path directory, String name) {
		var file = directory.resolve(name);
		if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
			throw new IllegalArgumentException();
		return file;
	}

	static void checkSchema(Path file, String expectedFingerprint) throws IOException, XMLStreamException {
		var bytes = BoundedMigrationFile.read(file, 16_777_216L, "maxSchemaBytes", "Schema");
		if (!("sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(bytes)).equals(expectedFingerprint))
			throw new IllegalArgumentException();
		// The resolver checks this same fingerprint again before parsing the Schema
		// model.
		var factory = XMLInputFactory.newDefaultFactory();
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		var reader = factory.createXMLStreamReader(new java.io.ByteArrayInputStream(bytes),
				StandardCharsets.UTF_8.name());
		try {
			while (reader.hasNext()) {
				int event = reader.next();
				if (event == XMLStreamConstants.DTD)
					throw new IllegalArgumentException();
				if (event == XMLStreamConstants.START_ELEMENT)
					return;
			}
			throw new IllegalArgumentException();
		} finally {
			reader.close();
		}
	}

	private static Report report(Path file, String key) throws IOException {
		var bytes = BoundedMigrationFile.read(file, MAX_REPORT_BYTES, "maxStagingReportBytes", "Staging report");
		var node = MAPPER
				.readTree(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString());
		if (!node.path("formatVersion").isIntegralNumber() || node.path("formatVersion").intValue() != 1
				|| !node.path(key).isObject())
			throw new IllegalArgumentException();
		return new Report(node, "sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(bytes));
	}

	private static List<TransferStagedReplacement.Target> targets(JsonNode array) {
		var targets = new ArrayList<TransferStagedReplacement.Target>();
		for (var value : array(array))
			targets.add(new TransferStagedReplacement.Target(text(value, "taskId"), optionalText(value, "catalog"),
					text(value, "schema"), text(value, "table"), text(value, "stageTable")));
		return targets;
	}

	private static BulkMigrationJobVerificationResult verification(JsonNode node, String fingerprint) {
		if (!fingerprint.equals(text(node, "planFingerprint")))
			throw new IllegalArgumentException();
		var tasks = new ArrayList<BulkMigrationJobTaskVerificationResult>();
		for (var task : array(node.path("tasks"))) {
			var value = task.path("verificationResult");
			tasks.add(readVerification(text(task, "taskId"), value));
		}
		var result = new BulkMigrationJobVerificationResult(fingerprint, tasks);
		if (tasks.isEmpty() || !result.isMatch())
			throw new IllegalArgumentException();
		return result;
	}

	private static BulkMigrationJobTaskVerificationResult readVerification(String taskId, JsonNode value) {
		var columns = new ArrayList<String>();
		for (var column : array(value.path("columns"))) {
			if (!column.isString() || column.stringValue().isBlank())
				throw new IllegalArgumentException();
			columns.add(column.stringValue());
		}
		var chunks = new ArrayList<BulkMigrationVerificationChunk>();
		for (var chunk : array(value.path("chunks"))) {
			String expectedHash = text(chunk, "expectedHash");
			if (!expectedHash.startsWith(BulkMigrationUnorderedVerifier.HASH_PREFIX))
				throw new IllegalArgumentException();
			chunks.add(new BulkMigrationVerificationChunk(number(chunk, "index"),
					Math.toIntExact(number(chunk, "expectedRows")), Math.toIntExact(number(chunk, "actualRows")),
					expectedHash, text(chunk, "actualHash")));
		}
		var result = new BulkMigrationVerificationResult(Math.toIntExact(number(value, "chunkSize")),
				number(value, "expectedRows"), number(value, "actualRows"), columns, chunks);
		return new BulkMigrationJobTaskVerificationResult(taskId, columns, result);
	}

	private static JsonNode array(JsonNode node) {
		if (!node.isArray())
			throw new IllegalArgumentException();
		return node;
	}

	private static String optionalText(JsonNode node, String name) {
		return node.path(name).isNull() || node.path(name).isMissingNode() ? null : text(node, name);
	}

	private static String text(JsonNode node, String name) {
		var value = node.path(name);
		if (!value.isString() || value.stringValue().isBlank())
			throw new IllegalArgumentException();
		return value.stringValue();
	}

	private static long number(JsonNode node, String name) {
		var value = node.path(name);
		if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0)
			throw new IllegalArgumentException();
		return value.longValue();
	}
}
