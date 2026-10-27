/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

/**
 * Target-only command adapter. Mutation requires explicit authorization and a
 * pinned manifest.
 */
@lombok.Getter
@lombok.Setter
public class ImportTransferFileCommand extends AbstractTransferFileCommand {
	private java.io.File manifestFile;
	private String expectedManifestFingerprint;
	private java.io.File workspaceDirectory;
	private boolean allowExecution;
	@lombok.Setter(lombok.AccessLevel.NONE)
	private TransferFileImportService.Outcome result;

	/** Can run before a Gradle task constructs any connection pool. */
	public void validateAuthorization() {
		if (!allowExecution)
			throw new IllegalArgumentException("Set allowExecution explicitly to authorize CSV import.");
		if (expectedManifestFingerprint == null || !expectedManifestFingerprint.matches("sha256:[0-9a-f]{64}"))
			throw new IllegalArgumentException("A reviewed expectedManifestFingerprint is required for CSV import.");
	}

	public void validateInputs() throws Exception {
		validateAuthorization();
		if (manifestFile == null || workspaceDirectory == null)
			throw new IllegalArgumentException("manifestFile and workspaceDirectory are required.");
		new TransferFileImportService().validatePackage(manifestFile.toPath(), expectedManifestFingerprint);
	}

	@Override
	protected void doRun() {
		result = null;
		withRuntimeSource(() -> {
			validateAuthorization();
			if (manifestFile == null || workspaceDirectory == null)
				throw new IllegalArgumentException("manifestFile and workspaceDirectory are required.");
			final var snapshot = new TransferFilePackageIO().read(manifestFile.toPath(), expectedManifestFingerprint);
			final var definition = new TransferJobConfigurationIO()
					.read(snapshot.file().getParent().resolve("transfer.yaml"));
			final var bindings = java.util.Map.of(definition.configuration().target(), getDataSource());
			java.nio.file.Files.createDirectories(workspaceDirectory.toPath());
			final var service = new TransferFileImportService();
			try {
				final var review = service.review(manifestFile.toPath(), expectedManifestFingerprint, bindings);
				if (!review.schemaPreparation().warnings().isEmpty())
					logger.warn("Proceeding with {} compatibility warning(s); inspect generated DDL and import audit.",
							review.schemaPreparation().warnings().size());
				result = service.execute(review, bindings, workspaceDirectory.toPath());
			} catch (TransferFileImportService.ImportException failure) {
				result = failure.outcome();
				throw failure;
			}
			return null;
		});
		info("CSV import audit: ", result.auditFile());
	}
}
