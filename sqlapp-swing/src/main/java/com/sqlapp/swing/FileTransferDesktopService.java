/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.nio.file.Path;
import java.util.Map;
import java.util.Arrays;
import com.sqlapp.data.db.command.migration.transfer.*;

/**
 * Desktop connection lifetime only; all file/copy semantics belong to shared
 * commands.
 */
final class FileTransferDesktopService implements FileTransferPanel.Backend {
	record Reviewed(TransferFileImportService.Review review, TransferFilePackageIO.Manifest manifest) {
	}

	@Override
	public Object perform(FileTransferPanel.Action action, Path input, Path workspace, PlanningJdbc.Spec spec,
			char[] password, String schema, Reviewed reviewed) throws Exception {
		try {
			if (action == FileTransferPanel.Action.TEST)
				return new JdbcConnectionTestService().test(spec, password);
			if (action == FileTransferPanel.Action.DISCOVER) {
				final var prepared = new DataMigrationDraftService().discover(spec, password, schema, workspace);
				return new DataMigrationDraftService.Prepared(prepared.directory(),
						new TransferFileJobDraftBuilder().inspect(prepared.schema()));
			}
			if (action == FileTransferPanel.Action.EXPORT) {
				final var definition = new TransferFileExportService().inspectConfiguration(input);
				try (var pool = PlanningJdbc.open(spec, password)) {
					return new TransferFileExportService().export(input,
							Map.of(definition.configuration().source(), pool));
				}
			}
			final var manifest = action == FileTransferPanel.Action.IMPORT
					? new TransferFilePackageIO().read(input, reviewed.review().manifestFingerprint())
					: new TransferFilePackageIO().read(input, null);
			final var definition = new TransferJobConfigurationIO()
					.read(manifest.file().getParent().resolve("transfer.yaml"));
			try (var pool = action == FileTransferPanel.Action.IMPORT
					? PlanningJdbc.openForDataExecution(spec, password)
					: PlanningJdbc.open(spec, password)) {
				final var bindings = Map.of(definition.configuration().target(), pool);
				final var service = new TransferFileImportService();
				if (action == FileTransferPanel.Action.IMPORT)
					return service.execute(reviewed.review(), bindings, workspace);
				return new Reviewed(service.review(input, manifest.fingerprint(), bindings), manifest.manifest());
			}
		} catch (TransferFileImportService.ImportException failure) {
			throw failure;
		} catch (TransferSchemaPreparation.ValidationException failure) {
			throw failure;
		} catch (Exception failure) {
			throw new IllegalArgumentException(
					"CSV operation failed. Check the connection, supported scalar columns/numeric keys, "
							+ "existing empty target tables, file settings and package integrity. No automatic retry or target reset was performed.");
		} finally {
			Arrays.fill(password, '\0');
		}
	}
}
