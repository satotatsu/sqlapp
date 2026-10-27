/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

/** Source-only command adapter for GUI-independent CSV package creation. */
@lombok.Getter
@lombok.Setter
public class ExportTransferFileCommand extends AbstractTransferFileCommand {
	private java.io.File configurationFile;
	@lombok.Setter(lombok.AccessLevel.NONE)
	private TransferFilePackageIO.Snapshot result;

	public void validateInputs() throws java.io.IOException {
		if (configurationFile == null)
			throw new IllegalArgumentException("configurationFile is required.");
		new TransferFileExportService().inspectConfiguration(configurationFile.toPath());
	}

	@Override
	protected void doRun() {
		result = null;
		withRuntimeSource(() -> {
			if (configurationFile == null)
				throw new IllegalArgumentException("configurationFile is required.");
			final var definition = new TransferJobConfigurationIO().read(configurationFile.toPath());
			result = new TransferFileExportService().export(configurationFile.toPath(),
					java.util.Map.of(definition.configuration().source(), getDataSource()));
			return null;
		});
		info("CSV manifest: ", result.file(), " fingerprint=", result.fingerprint());
		if (!result.manifest().warnings().isEmpty())
			logger.warn(
					"CSV export completed with {} temporal warning(s). Inspect manifest.warnings before conversion/import.",
					result.manifest().warnings().size());
	}
}
