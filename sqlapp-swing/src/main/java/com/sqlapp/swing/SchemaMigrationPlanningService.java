/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

import javax.sql.DataSource;

import com.sqlapp.data.db.command.ConsoleOutputLevel;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.data.db.command.migration.schema.MigrationPlanArtifact;
import com.sqlapp.data.db.command.migration.schema.MigrationPlanCommand;
import com.sqlapp.data.db.command.migration.schema.MigrationPlanIO;
import com.sqlapp.data.db.command.migration.schema.MigrationCommand;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.util.JsonConverter;

/**
 * Composes the existing read-only planning command; it never executes migration
 * SQL.
 */
final class SchemaMigrationPlanningService {
	record Request(Path upDirectory, Path downDirectory, Path setupDirectory, Path finalizeDirectory,
			Long targetVersion, String historyTable, boolean checksumValidation, boolean rejectOutOfOrder,
			boolean rejectNonTransactional, boolean requireDownMigration, boolean repeatableMigrations,
			boolean recursive) {
		Request {
			upDirectory = directory(upDirectory, "Up SQL directory", true);
			downDirectory = directory(downDirectory, "Down SQL directory", false);
			setupDirectory = directory(setupDirectory, "Setup SQL directory", false);
			finalizeDirectory = directory(finalizeDirectory, "Finalize SQL directory", false);
			if (targetVersion != null && targetVersion < 0) {
				throw new IllegalArgumentException("Target version must be a non-negative integer.");
			}
			historyTable = historyTable == null || historyTable.isBlank() ? "changelog" : historyTable.trim();
		}

		private static Path directory(final Path path, final String label, final boolean required) {
			if (path == null && !required) {
				return null;
			}
			if (path == null || !Files.isDirectory(path) || !Files.isReadable(path)) {
				throw new IllegalArgumentException(label + " must be an existing readable directory.");
			}
			return path.toAbsolutePath().normalize();
		}
	}

	record Result(MigrationPlanArtifact artifact, ReportReviewService.Review review) {
	}

	/**
	 * The supplied password array is cleared after this operation, including
	 * failure.
	 */
	Result plan(final Request request, final String jdbcUrl, final String username, final char[] password,
			final String driverClass) {
		Objects.requireNonNull(password, "password");
		final String secret = new String(password);
		try {
			Objects.requireNonNull(request, "request");
			try (var source = PlanningJdbc.open(new PlanningJdbc.Spec(jdbcUrl, username, driverClass), password)) {
				return plan(request, source);
			}
		} catch (final RuntimeException exception) {
			String message = exception.getMessage() == null ? exception.getClass().getSimpleName()
					: exception.getMessage();
			if (!secret.isEmpty()) {
				message = message.replace(secret, "[redacted]");
			}
			if (jdbcUrl != null && !jdbcUrl.isEmpty()) {
				message = message.replace(jdbcUrl, "[JDBC URL]");
			}
			throw new IllegalArgumentException("Unable to build migration plan: " + message);
		} finally {
			Arrays.fill(password, '\0');
		}
	}

	Result plan(final Request request, final DataSource source) {
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(source, "source");
		final var command = new MigrationPlanCommand() {
			@Override
			public Dialect getDialect(final java.sql.Connection connection) {
				return PlanningJdbc.requireDialect(connection);
			}
		};
		command.setDataSource(source);
		configure(command, request);
		command.run();
		final var artifact = MigrationPlanArtifact.of(Objects.requireNonNull(command.getPlan(), "Generated plan"));
		return new Result(artifact, new ReportReviewService().schemaPlan(artifact));
	}

	static void configure(final MigrationCommand command, final Request request) {
		command.setCloseDataSource(false);
		command.setConsoleOutputLevel(ConsoleOutputLevel.ERROR);
		command.setSqlDirectory(request.upDirectory().toFile());
		command.setDownSqlDirectory(request.downDirectory() == null ? null : request.downDirectory().toFile());
		command.setSetupSqlDirectory(request.setupDirectory() == null ? null : request.setupDirectory().toFile());
		command.setFinalizeSqlDirectory(
				request.finalizeDirectory() == null ? null : request.finalizeDirectory().toFile());
		command.setLastChangeToApply(request.targetVersion() == null ? Long.MAX_VALUE : request.targetVersion());
		command.setSchemaChangeLogTableName(request.historyTable());
		command.setChecksumValidation(request.checksumValidation());
		command.setRejectOutOfOrder(request.rejectOutOfOrder());
		command.setRejectNonTransactional(request.rejectNonTransactional());
		command.setRequireDownMigration(request.requireDownMigration());
		command.setRepeatableMigrations(request.repeatableMigrations());
		command.setRecursive(request.recursive());
		command.setEncoding("UTF-8");
	}

	/**
	 * Saves the artifact shown to the user, retaining its timestamp and
	 * fingerprint.
	 */
	void save(final Result result, final Path destination) throws IOException {
		Objects.requireNonNull(result, "result");
		if (destination == null || !destination.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
			throw new IllegalArgumentException("Choose a .json file for the migration plan.");
		}
		final var converter = new JsonConverter();
		converter.setIndentOutput(true);
		AtomicMigrationFile.write(destination, temporary -> {
			converter.writeJsonValue(temporary.toFile(), result.artifact());
			if (!result.artifact().equals(new MigrationPlanIO().read(temporary))) {
				throw new IOException("Saved migration plan differs from the displayed plan.");
			}
		});
	}
}
