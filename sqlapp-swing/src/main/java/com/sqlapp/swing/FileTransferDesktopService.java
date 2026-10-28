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
	private static final class PackageSelectionException extends IllegalArgumentException {
		private static final long serialVersionUID = 1L;

		PackageSelectionException() {
			super("The selected package folder must contain manifest.json directly. Choose a completed export folder, not its parent or CSV subfolder.");
		}
	}

	record Reviewed(TransferFileImportService.Review review, TransferFilePackageIO.Manifest manifest) {
	}

	record Inspected(TransferFilePackageIO.Snapshot snapshot, boolean valuesValidated,
			TransferJobConfiguration configuration) {
		Inspected(TransferFilePackageIO.Snapshot snapshot) {
			this(snapshot, false, null);
		}
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
			final var manifestPath = java.nio.file.Files.isDirectory(input) ? input.resolve("manifest.json") : input;
			if (java.nio.file.Files.isDirectory(input) && !java.nio.file.Files.isRegularFile(manifestPath))
				throw new PackageSelectionException();
			final var manifest = action == FileTransferPanel.Action.IMPORT
					? new TransferFilePackageIO().read(manifestPath, reviewed.review().manifestFingerprint())
					: new TransferFilePackageIO().read(manifestPath, null);
			final var definition = new TransferJobConfigurationIO()
					.read(manifest.file().getParent().resolve("transfer.yaml"));
			if (!definition.fingerprint().equals(manifest.manifest().jobFingerprint()))
				throw new IllegalArgumentException("Package job changed.");
			if (action.isInspection()) {
				if (action == FileTransferPanel.Action.INSPECT_DATA)
					new TransferFileImportService().validatePackage(manifest.file(), manifest.fingerprint());
				return new Inspected(manifest, action == FileTransferPanel.Action.INSPECT_DATA,
						definition.configuration());
			}
			try (var pool = action == FileTransferPanel.Action.IMPORT
					? PlanningJdbc.openForDataExecution(spec, password)
					: PlanningJdbc.open(spec, password)) {
				final var bindings = Map.of(definition.configuration().target(), pool);
				final var service = new TransferFileImportService();
				if (action == FileTransferPanel.Action.IMPORT)
					return service.execute(reviewed.review(), bindings, workspace);
				return new Reviewed(service.review(manifest.file(), manifest.fingerprint(), bindings),
						manifest.manifest());
			}
		} catch (TransferFileImportService.ImportException failure) {
			throw failure;
		} catch (TransferSchemaPreparation.ValidationException failure) {
			throw new TransferPreparationFailure(failure);
		} catch (PackageSelectionException failure) {
			throw failure;
		} catch (Exception failure) {
			if (action == FileTransferPanel.Action.INSPECT_DATA)
				throw new IllegalArgumentException(
						"CSV value validation failed. Check recorded row counts, headers, types, keys and export warnings. Use package-only inspection to view warnings. No database connection was opened.");
			if (action.isInspection())
				throw new IllegalArgumentException(
						"Package inspection failed. Check manifest.json, all package members and their recorded sizes/hashes. No database connection was opened.");
			throw new IllegalArgumentException(
					"CSV operation failed. Check the connection, supported scalar columns/numeric keys, "
							+ "existing empty target tables, file settings and package integrity. No automatic retry or target reset was performed.");
		} finally {
			Arrays.fill(password, '\0');
		}
	}
}
