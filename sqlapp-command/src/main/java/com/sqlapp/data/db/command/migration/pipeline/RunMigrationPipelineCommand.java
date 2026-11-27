/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.pipeline;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.sql.DataSource;

import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.data.db.command.migration.model.ExecuteMigrationModelsCommand;
import com.sqlapp.data.db.command.migration.model.MigrationModelConfigurationResolver;
import com.sqlapp.data.db.command.migration.model.MigrationModelDataTests;
import com.sqlapp.data.db.command.migration.model.MigrationModelTargetMapping;
import com.sqlapp.data.db.command.migration.pipeline.MigrationPipelineReport.Status;
import com.sqlapp.data.db.command.migration.pipeline.MigrationPipelineReport.Step;
import com.sqlapp.data.db.command.migration.pipeline.MigrationPipelineReport.StepReport;
import com.sqlapp.data.db.command.migration.transfer.TransferDatabaseCommand;
import com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration;
import com.sqlapp.data.db.command.migration.transfer.TransferJobConfigurationIO;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingIO;
import com.sqlapp.data.db.command.migration.transfer.TransferStagedReplacement;
import com.sqlapp.util.JsonConverter;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.MessageDigests;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * Runs load, models, data tests and publish as one job: a verified transfer
 * from the source database into the engine (optional), the model unit tests and
 * builds inside the engine, the data tests of the built models, and a verified
 * transfer of the model tables to the target database.
 * <p>
 * Without {@link #allowExecution} the command reads the definitions offline and
 * reports the fingerprint to approve. It covers the pipeline file, both
 * transfer jobs, the model YAML and every model definition. Execution requires
 * that fingerprint, and each step checks that its definition is still the
 * approved one.
 * </p>
 * <p>
 * Publishing starts only after every model of this run was built and every
 * ERROR data test passed. The declared columns of this run are written into the
 * run directory as the contract mapping of the publish transfer; the publish
 * job must not name a mapping file itself. Every run gets its own directory
 * with a report that is rewritten after each step. A failed run is not resumed:
 * run the pipeline again. Staging tables that a failed publish retained are
 * dropped by the next run when that replacement settled (it did not start,
 * rolled back, or committed); after an unconfirmed commit or rollback the run
 * stops for inspection.
 * </p>
 * The engine is {@link #getDataSource()}.
 */
@Getter
@Setter
public class RunMigrationPipelineCommand extends AbstractDataSourceCommand {
	private static final DateTimeFormatter RUN_ID = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'", Locale.ROOT)
			.withZone(ZoneOffset.UTC);

	private File configurationFile;
	private File workspaceDirectory;
	private String expectedConfigurationFingerprint;
	private boolean allowExecution;
	/**
	 * Rebuild incremental models from all rows; required after their definitions or
	 * the definitions they read changed.
	 */
	private boolean fullRefresh;
	private DataSource sourceDataSource;
	private DataSource targetDataSource;
	@Setter(AccessLevel.NONE)
	private String configurationFingerprint;
	/** Detail of the running step that is kept when it fails. */
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private String stepNote;
	@Setter(AccessLevel.NONE)
	private Path runDirectory;
	@Setter(AccessLevel.NONE)
	private Path reportFile;
	@Setter(AccessLevel.NONE)
	private MigrationPipelineReport report;

	/** Definitions read for one validation, with their fingerprints. */
	public record Inputs(MigrationPipelineConfiguration.Snapshot pipeline, TransferJobConfigurationIO.Snapshot load,
			String modelsFingerprint, MigrationModelConfigurationResolver.Resolution models,
			TransferJobConfigurationIO.Snapshot publish, String fingerprint) {
	}

	/**
	 * Reads and checks every definition without connecting, and computes the
	 * fingerprint to approve.
	 */
	public Inputs validateInputs() throws IOException {
		if (configurationFile == null) {
			throw new IllegalArgumentException("configurationFile is required.");
		}
		if (allowExecution && (expectedConfigurationFingerprint == null
				|| !expectedConfigurationFingerprint.matches("sha256:[0-9a-f]{64}"))) {
			throw new IllegalArgumentException(
					"Executing requires an expectedConfigurationFingerprint (sha256:<64 lowercase hex digits>).");
		}
		final var pipeline = MigrationPipelineConfiguration.read(configurationFile.toPath());
		final var io = new TransferJobConfigurationIO();
		final var load = pipeline.load() == null ? null : io.read(pipeline.load());
		if (load != null) {
			checkTransfer("load", load);
		}
		final var publish = io.read(pipeline.publish());
		checkTransfer("publish", publish);
		if (publish.configuration().mappingFile() != null) {
			throw new IllegalArgumentException("The publish job must not set mappingFile: the pipeline passes the"
					+ " declared columns of the run as the mapping.");
		}
		final var models = new MigrationModelConfigurationResolver().resolve(pipeline.models().toFile());
		final String modelsFingerprint = modelsFingerprint(pipeline.models(), models);
		final String text = String.join("\n", "pipeline " + pipeline.fingerprint(),
				"load " + (load == null ? "-" : load.fingerprint()), "models " + modelsFingerprint,
				"publish " + publish.fingerprint());
		return new Inputs(pipeline, load, modelsFingerprint, models, publish, "sha256:"
				+ MessageDigests.SHA256.checksumAsString(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
	}

	private static void checkTransfer(final String step, final TransferJobConfigurationIO.Snapshot job) {
		final var configuration = job.configuration();
		if (configuration.dataMode() != null
				&& configuration.dataMode() != TransferJobConfiguration.DataMode.JDBC_DIRECT) {
			throw new IllegalArgumentException("The " + step + " job must copy rows directly (dataMode: JDBC_DIRECT).");
		}
		if (Boolean.FALSE.equals(configuration.verify())) {
			throw new IllegalArgumentException(
					"The " + step + " job must verify the copied rows; remove verify: false.");
		}
	}

	private static String modelsFingerprint(final Path file,
			final MigrationModelConfigurationResolver.Resolution models) throws IOException {
		return "sha256:" + MessageDigests.SHA256.checksumAsString(
				(MessageDigests.SHA256.checksumAsString(Files.readAllBytes(file)) + "\n" + models.plan().fingerprint())
						.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	@Override
	protected void doRun() {
		configurationFingerprint = null;
		runDirectory = null;
		reportFile = null;
		report = null;
		try {
			runPipeline();
		} finally {
			if (isCloseDataSource()) {
				close(sourceDataSource);
				if (targetDataSource != sourceDataSource) {
					close(targetDataSource);
				}
				if (getDataSource() != sourceDataSource && getDataSource() != targetDataSource) {
					close(getDataSource());
				}
			}
		}
	}

	private void runPipeline() {
		final Inputs inputs;
		try {
			inputs = validateInputs();
		} catch (IOException e) {
			throw new CommandException("Migration pipeline definitions cannot be read: " + e.getMessage(), e);
		}
		configurationFingerprint = inputs.fingerprint();
		info("Migration pipeline fingerprint: ", configurationFingerprint);
		if (!allowExecution) {
			info("Migration pipeline review only; set allowExecution and expectedConfigurationFingerprint to run it.");
			return;
		}
		if (!configurationFingerprint.equals(expectedConfigurationFingerprint)) {
			throw new IllegalArgumentException("Migration pipeline definitions changed. Review them again.");
		}
		if (workspaceDirectory == null || getDataSource() == null || targetDataSource == null
				|| inputs.load() != null && sourceDataSource == null) {
			throw new IllegalArgumentException("workspaceDirectory, the engine dataSource, targetDataSource and,"
					+ " with a load job, sourceDataSource are required.");
		}
		final String runId = RUN_ID.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 8);
		try {
			runDirectory = Files.createDirectories(workspaceDirectory.toPath().resolve(runId));
			reportFile = runDirectory.resolve("pipeline-report.json");
			report = new MigrationPipelineReport(1, runId, configurationFingerprint, Status.RUNNING,
					List.of(StepReport.of(Step.LOAD, inputs.load() == null ? Status.SKIPPED : Status.NOT_RUN),
							StepReport.of(Step.MODELS, Status.NOT_RUN),
							StepReport.of(Step.DATA_TESTS,
									inputs.models().dataTests().isEmpty() ? Status.SKIPPED : Status.NOT_RUN),
							StepReport.of(Step.PUBLISH, Status.NOT_RUN)));
			report.write(reportFile);
		} catch (IOException e) {
			throw new CommandException("Migration pipeline run directory cannot be written: " + e.getMessage(), e);
		}
		info("Migration pipeline run: ", runDirectory);
		if (inputs.load() != null) {
			step(Step.LOAD, inputs.load().fingerprint(), () -> transfer(inputs.load().file(),
					inputs.load().fingerprint(), sourceDataSource, getDataSource(), runDirectory.resolve("load")));
		}
		// Models and data tests share one transaction: the models are committed only
		// when every ERROR test passed.
		step(Step.MODELS, inputs.modelsFingerprint(), () -> models(inputs));
		step(Step.PUBLISH, inputs.publish().fingerprint(), () -> publish(inputs));
		finish(Status.SUCCEEDED);
		info("Migration pipeline completed: ", reportFile);
	}

	/** Result of a step for the report. */
	private record Outcome(String reviewFile, String resultFile, String detail) {
	}

	@FunctionalInterface
	private interface StepAction {
		Outcome run() throws Exception;
	}

	private void step(final Step step, final String fingerprint, final StepAction action) {
		final Instant started = Instant.now();
		final long start = System.nanoTime();
		update(new StepReport(step, Status.RUNNING, started.toString(), null, fingerprint, null, null, null, null),
				Status.RUNNING);
		info("Migration pipeline step ", step, " started");
		try {
			final Outcome outcome = action.run();
			stepNote = null;
			update(new StepReport(step, Status.SUCCEEDED, started.toString(), elapsed(start), fingerprint,
					outcome.reviewFile(), outcome.resultFile(), outcome.detail(), null), Status.RUNNING);
			info("Migration pipeline step ", step, " succeeded",
					outcome.detail() == null ? "" : ": " + outcome.detail());
		} catch (final DataTestsFailed e) {
			update(new StepReport(step, Status.FAILED, started.toString(), elapsed(start), fingerprint, null, null,
					null, "Rolled back: data tests failed."), Status.RUNNING);
			update(new StepReport(Step.DATA_TESTS, Status.FAILED, started.toString(), elapsed(start), fingerprint, null,
					null, e.summary, e.getMessage()), Status.FAILED);
			throw new CommandException("Migration pipeline stopped at " + Step.DATA_TESTS + ": " + e.getMessage()
					+ " Report: " + reportFile, e);
		} catch (final Exception e) {
			final String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			final StepReport failed = new StepReport(step, Status.FAILED, started.toString(), elapsed(start),
					fingerprint, failedFile(e, true), failedFile(e, false), stepNote, message);
			stepNote = null;
			update(failed, Status.FAILED);
			throw new CommandException(
					"Migration pipeline stopped at " + step + ": " + message + " Report: " + reportFile, e);
		}
	}

	/**
	 * Transfer reports of a failed transfer step, kept by the command that failed.
	 */
	private String failedFile(final Exception e, final boolean review) {
		return e instanceof TransferStepException failure ? (review ? failure.reviewFile : failure.resultFile) : null;
	}

	private static Long elapsed(final long start) {
		return (System.nanoTime() - start) / 1_000_000L;
	}

	private void update(final StepReport step, final Status overall) {
		report = report.with(step, overall);
		try {
			report.write(reportFile);
		} catch (IOException e) {
			throw new CommandException("Migration pipeline report cannot be written: " + reportFile, e);
		}
	}

	private void finish(final Status status) {
		report = new MigrationPipelineReport(report.formatVersion(), report.runId(), report.pipelineFingerprint(),
				status, report.steps());
		try {
			report.write(reportFile);
		} catch (IOException e) {
			throw new CommandException("Migration pipeline report cannot be written: " + reportFile, e);
		}
	}

	private Outcome models(final Inputs inputs) throws IOException {
		requireUnchanged(inputs);
		final ExecuteMigrationModelsCommand command = new ExecuteMigrationModelsCommand();
		command.setConfigurationFile(inputs.pipeline().models().toFile());
		command.setDataSource(getDataSource());
		command.setCloseDataSource(false);
		command.setUnitTestsEnabled(true);
		command.setDataTestsEnabled(true);
		command.setFullRefresh(fullRefresh);
		try {
			command.run();
		} catch (final RuntimeException e) {
			final var results = command.getDataTestResults();
			if (command.getResult() != null && command.getResult().failures().isEmpty() && results != null
					&& !results.isSuccess()) {
				throw new DataTestsFailed(results);
			}
			throw e;
		}
		// The publish step uses the declarations of the built models only.
		requireUnchanged(inputs);
		final var results = command.getDataTestResults();
		if (results != null && !results.tests().isEmpty()) {
			update(new StepReport(Step.DATA_TESTS, Status.SUCCEEDED, Instant.now().toString(), null,
					inputs.modelsFingerprint(), null, null, results.summary(), null), Status.RUNNING);
		}
		return new Outcome(null, null, "unit tests passed: " + command.getUnitTestResults().size() + ", models built: "
				+ command.getResult().models().size() + (fullRefresh ? ", fullRefresh" : ""));
	}

	/** Data tests failed and the models of the run were rolled back. */
	private static final class DataTestsFailed extends RuntimeException {
		private static final long serialVersionUID = 1L;
		private final String summary;

		DataTestsFailed(final MigrationModelDataTests.Results results) {
			super("Data tests failed (" + results.summary() + "): " + results.tests().stream()
					.filter(test -> test.status() != MigrationModelDataTests.Status.PASS
							&& test.status() != MigrationModelDataTests.Status.WARN)
					.map(test -> test.id() + ": " + test.message()).collect(java.util.stream.Collectors.joining("; ")));
			this.summary = results.summary();
		}
	}

	private void requireUnchanged(final Inputs inputs) throws IOException {
		final var models = new MigrationModelConfigurationResolver().resolve(inputs.pipeline().models().toFile());
		if (!modelsFingerprint(inputs.pipeline().models(), models).equals(inputs.modelsFingerprint())) {
			throw new IllegalStateException("Model definitions changed during the run. Review the pipeline again.");
		}
	}

	private Outcome publish(final Inputs inputs) throws Exception {
		final var io = new TransferJobConfigurationIO();
		final var current = io.read(inputs.publish().file());
		if (!current.fingerprint().equals(inputs.publish().fingerprint())) {
			throw new IllegalStateException("The publish job changed during the run. Review the pipeline again.");
		}
		final var job = current.configuration();
		final var mapping = contract(inputs, job.tables());
		final Path directory = Files.createDirectories(runDirectory.resolve("publish-job"));
		String mappingFile = null;
		if (!mapping.tables().isEmpty()) {
			final var mappingIO = new TransferNameMappingIO();
			mappingIO.write(directory.resolve("model-types.yaml"), mappingIO.format(mapping), null);
			mappingFile = "model-types.yaml";
		}
		final var derived = new TransferJobConfiguration(job.formatVersion(), job.source(), job.target(), job.tables(),
				job.schemaMode(), job.sourceSchema(), job.targetSchema(), mappingFile, job.chunkSize(), job.verify(),
				job.dataMode(), job.files(), job.compatibilityPolicy(), job.allowDestructiveSchemaChanges(),
				job.generatedKeyColumn(), job.insertMethod(), job.targetPreparation(), job.generatedKeyCounters());
		final var written = io.write(directory.resolve("publish.yaml"), io.format(derived), null);
		stepNote = releaseRetainedStage();
		final Outcome outcome = transfer(written.file(), written.fingerprint(), getDataSource(), targetDataSource,
				runDirectory.resolve("publish"));
		return new Outcome(outcome.reviewFile(), outcome.resultFile(),
				stepNote == null ? outcome.detail() : outcome.detail() + ", " + stepNote);
	}

	/**
	 * Drops the staging tables that the latest earlier failed publish retained,
	 * when its staged replacement settled. Runs are read newest first; a run that
	 * published successfully ends the search, since a successful replacement drops
	 * its stages. An unconfirmed commit or rollback stops the run.
	 *
	 * @return a note for the report, or null when nothing was dropped
	 */
	private String releaseRetainedStage() throws IOException {
		final List<Path> runs;
		try (var stream = Files.list(workspaceDirectory.toPath())) {
			runs = stream.filter(Files::isDirectory).filter(run -> !run.equals(runDirectory))
					.sorted(java.util.Comparator.comparing(Path::getFileName).reversed()).toList();
		}
		for (final Path run : runs) {
			final Path file = run.resolve("pipeline-report.json");
			if (!Files.isRegularFile(file)) {
				continue;
			}
			final MigrationPipelineReport earlier;
			try {
				earlier = new JsonConverter().fromJsonString(Files.readString(file), MigrationPipelineReport.class);
			} catch (RuntimeException e) {
				continue;
			}
			final StepReport publish = earlier.step(Step.PUBLISH);
			if (publish.status() == Status.SUCCEEDED) {
				return null;
			}
			if (publish.status() != Status.FAILED || publish.resultFile() == null
					|| !Files.isRegularFile(Path.of(publish.resultFile()))) {
				continue;
			}
			final TransferStagedReplacement.Release release;
			try {
				release = TransferStagedReplacement.releaseSettledStage(Path.of(publish.resultFile()),
						targetDataSource);
			} catch (RuntimeException e) {
				throw new IllegalStateException("The failed publish of run " + earlier.runId()
						+ " retained staging tables that cannot be released: " + e.getMessage()
						+ " Compare them with its transfer result, then drop them before running again.", e);
			}
			if (release.droppedStages().isEmpty()) {
				return null;
			}
			final String note = "released staging of run " + earlier.runId() + " (" + release.state() + "): "
					+ String.join(", ", release.droppedStages());
			info("Migration pipeline ", note);
			return note;
		}
		return null;
	}

	/** Contract mapping of the published model tables with declared columns. */
	private static TransferNameMappingIO.Configuration contract(final Inputs inputs, final List<String> tables) {
		final var all = MigrationModelTargetMapping.of(inputs.models().plan());
		final var selected = new LinkedHashMap<String, TransferNameMappingIO.TableMapping>();
		all.tables().forEach((name, table) -> {
			final String key = name.toUpperCase(Locale.ROOT);
			if (tables.stream().map(value -> value.toUpperCase(Locale.ROOT))
					.anyMatch(value -> value.equals(key) || !key.contains(".") && value.endsWith("." + key))) {
				selected.put(name, table);
			}
		});
		return new TransferNameMappingIO.Configuration(1, selected);
	}

	private Outcome transfer(final Path job, final String fingerprint, final DataSource from, final DataSource to,
			final Path workspace) throws Exception {
		final TransferDatabaseCommand command = new TransferDatabaseCommand();
		command.setConfigurationFile(job.toFile());
		command.setWorkspaceDirectory(workspace.toFile());
		command.setExpectedConfigurationFingerprint(fingerprint);
		command.setAllowExecution(true);
		command.setSourceDataSource(from);
		command.setDataSource(to);
		command.setCloseDataSource(false);
		try {
			command.run();
		} catch (RuntimeException e) {
			throw new TransferStepException(e, path(command.getReviewFile()), path(command.getResultFile()));
		}
		final var result = command.getResult();
		final List<String> detail = new ArrayList<>();
		detail.add("phase: " + result.phase());
		if (result.verificationResult() != null) {
			detail.add("verified: " + result.verificationResult().isMatch());
		}
		return new Outcome(path(command.getReviewFile()), path(command.getResultFile()), String.join(", ", detail));
	}

	private static String path(final Path path) {
		return path == null ? null : path.toAbsolutePath().toString();
	}

	/** A failed transfer with the reports it left. */
	private static final class TransferStepException extends Exception {
		private static final long serialVersionUID = 1L;
		private final String reviewFile;
		private final String resultFile;

		TransferStepException(final RuntimeException cause, final String reviewFile, final String resultFile) {
			super(cause.getMessage(), cause);
			this.reviewFile = reviewFile;
			this.resultFile = resultFile;
		}
	}

	private static void close(final DataSource dataSource) {
		if (dataSource instanceof AutoCloseable closeable) {
			try {
				closeable.close();
			} catch (Exception ignored) {
				// Closing a pool must not hide the outcome of the run.
			}
		}
	}
}
