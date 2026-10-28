/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.io.File;
import java.nio.file.*;
import java.util.LinkedHashMap;
import javax.sql.DataSource;

/**
 * Common direct-JDBC job adapter. Defaults to review only; services own
 * migration semantics.
 */
@lombok.Getter
@lombok.Setter
public class TransferDatabaseCommand extends AbstractTransferFileCommand {
	private File configurationFile;
	private File workspaceDirectory;
	private String expectedConfigurationFingerprint;
	private boolean allowExecution;
	private DataSource sourceDataSource;
	@lombok.Setter(lombok.AccessLevel.NONE)
	private TransferJobExecutionService.Review review;
	@lombok.Setter(lombok.AccessLevel.NONE)
	private TransferJobExecutionService.Outcome result;
	@lombok.Setter(lombok.AccessLevel.NONE)
	private Path reviewFile;
	@lombok.Setter(lombok.AccessLevel.NONE)
	private Path resultFile;

	/**
	 * Offline authorization and format validation; call before constructing runtime
	 * pools.
	 */
	public void validateInputs() throws java.io.IOException {
		if (allowExecution && (expectedConfigurationFingerprint == null
				|| !expectedConfigurationFingerprint.matches("sha256:[0-9a-f]{64}")))
			throw new IllegalArgumentException(
					"Executing requires an expectedConfigurationFingerprint (sha256:<64 lowercase hex digits>).");
		if (configurationFile == null || workspaceDirectory == null)
			throw new IllegalArgumentException("configurationFile and workspaceDirectory are required.");
		final var definition = new TransferJobConfigurationIO().read(configurationFile.toPath());
		if (expectedConfigurationFingerprint != null
				&& !expectedConfigurationFingerprint.equals(definition.fingerprint()))
			throw new IllegalArgumentException("Transfer configuration changed. Review it again.");
		if (definition.configuration().dataMode() != TransferJobConfiguration.DataMode.JDBC_DIRECT
				|| definition.configuration().mappingFile() != null)
			throw new IllegalArgumentException("Direct execution requires JDBC_DIRECT and no explicit mappingFile.");
	}

	@Override
	protected void doRun() {
		review = null;
		result = null;
		reviewFile = null;
		resultFile = null;
		Throwable primary = null;
		try {
			withRuntimeSource(() -> {
				validateInputs();
				if (sourceDataSource == null)
					throw new IllegalArgumentException("sourceDataSource is required.");
				final var definition = new TransferJobConfigurationIO().read(configurationFile.toPath());
				final var bindings = new LinkedHashMap<String, DataSource>();
				bindings.put(definition.configuration().source(), sourceDataSource);
				if (definition.configuration().source().equals(definition.configuration().target())
						&& sourceDataSource != getDataSource())
					throw new IllegalArgumentException("Separate runtime connections require distinct logical names.");
				bindings.put(definition.configuration().target(), getDataSource());
				Files.createDirectories(workspaceDirectory.toPath());
				final var service = new TransferJobExecutionService();
				review = service.review(configurationFile.toPath(), bindings, workspaceDirectory.toPath());
				if (!definition.fingerprint().equals(review.prepared().job().fingerprint())
						|| expectedConfigurationFingerprint != null
								&& !expectedConfigurationFingerprint.equals(review.prepared().job().fingerprint()))
					throw new IllegalArgumentException("Transfer definition changed before review. Review again.");
				reviewFile = new TransferJobReportIO().writeReview(review);
				if (!review.prepared().warnings().isEmpty())
					logger.warn("Direct transfer has {} compatibility warning(s); inspect the review.",
							review.prepared().warnings().size());
				if (allowExecution) {
					try {
						result = service.execute(review, bindings);
					} catch (TransferJobExecutionService.ExecutionException failure) {
						result = failure.outcome();
						try {
							saveResult();
						} catch (Exception auditFailure) {
							failure.addSuppressed(new IllegalStateException(
									"Transfer result audit unavailable; inspect the retained Java outcome."));
						}
						throw failure;
					}
					saveResult();
				}
				return null;
			});
		} catch (RuntimeException | Error failure) {
			primary = failure;
			throw failure;
		} finally {
			if (isCloseDataSource() && sourceDataSource != getDataSource()
					&& sourceDataSource instanceof AutoCloseable closeable) {
				try {
					closeable.close();
				} catch (Exception failure) {
					final var safe = new IllegalStateException(
							"Source DataSource cleanup failed; inspect retained results before retrying.");
					if (primary == null)
						throw safe;
					primary.addSuppressed(safe);
				}
			}
		}
		info("Direct transfer review: ", reviewFile,
				allowExecution ? " result: " + resultFile : " (review only; no rows copied)");
	}

	private void saveResult() throws java.io.IOException {
		resultFile = new TransferJobReportIO().writeOutcome(review, result);
	}
}
