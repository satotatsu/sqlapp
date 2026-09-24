/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;
import com.sqlapp.exceptions.CommandException;

import lombok.Getter;
import lombok.Setter;

/** Builds a migration plan without creating/upgrading history or executing SQL. */
@Getter
@Setter
public class MigrationPlanCommand extends MigrationCommand {
	/** Optional JSON artifact destination. */
	private File outputFile;
	/** Optional reviewable SQL destination; never executed by this command. */
	private File dryRunOutputFile;
	/** Whether a plan containing known blockers should fail the command. */
	private boolean failOnBlockers;

	@Setter(lombok.AccessLevel.NONE)
	private MigrationPlan plan;

	@Override
	protected void doRun() {
		plan = null;
		requireValidationDirectory();
		final DbVersionHandler handler = createDbVersionHandler();
		executeNoTranAndClose(getDataSource(), connection -> {
			final var dialect = getDialect(connection);
			final var reader = new DbVersionFileHandler();
			reader.setUpSqlDirectory(getSqlDirectory());
			reader.setDownSqlDirectory(getDownSqlDirectory());
			reader.setRecursive(isRecursive());
			reader.setEncoding(getEncoding());
			reader.setSqlSplitter(dialect.createSqlSplitter());
			final var files = reader.read();
			final List<RepeatableMigrationFile> repeatableFiles = isRepeatableMigrations()
					? RepeatableMigrationFile.read(getSqlDirectory(), isRecursive(), getEncoding(), dialect.createSqlSplitter())
					: List.of();
			final Table definition = handler.createVersionTableDefinition(getSchemaChangeLogTableName());
			final Table existing = handler.getTable(connection, dialect, definition);
			final Table history;
			if (existing == null) {
				history = definition;
			} else {
				history = existing;
				handler.load(connection, dialect, history);
			}

			final var applied = new HashMap<Long, Status>();
			final var issues = new ArrayList<MigrationPlan.HistoryIssue>();
			Long current = null;
			for (final Row row : history.getRows()) {
				final Long version = handler.getId(row);
				if (version == null) {
					continue;
				}
				final Status status = handler.getStatus(row);
				applied.put(version, status);
				if (status.isCompleted() && (current == null || version > current)) {
					current = version;
				}
				if (status.isStarted() || status.isErrord()) {
					issues.add(new MigrationPlan.HistoryIssue(version, status));
				}
			}

			final var pending = new ArrayList<MigrationPlan.Entry>();
			final var outOfOrder = new ArrayList<Long>();
			final long target = getLastChangeToApply() != null ? getLastChangeToApply()
					: current != null ? current : Long.MAX_VALUE;
			for (final var file : files) {
				final long version = file.getVersionNumber();
				if (version > target || applied.containsKey(version)) {
					continue;
				}
				final var source = file.getUpSqlFile();
				final boolean transactional = !getNoTransactionFileFilter().test(source);
				pending.add(new MigrationPlan.Entry(version, source == null ? null : source.getName(),
						source == null ? null : source.getAbsolutePath(), file.getUpSqls().size(), transactional,
						isChecksumValidation(), file.getDownSqls() != null && !file.getDownSqls().isEmpty(),
						file.getUpSqlChecksum()));
				if (current != null && version < current) {
					outOfOrder.add(version);
				}
			}

			final MigrationValidationResult validation = MigrationChecksumValidator.validate(history, handler, files);
			final var drift = getPreMigrationSchemaFile() == null ? null : assessPreMigrationDrift(connection);
			final var repeatablePending = new ArrayList<MigrationPlan.RepeatableEntry>();
			if (!repeatableFiles.isEmpty()) {
				final var repeatableHandler = new RepeatableMigrationHandler();
				final Table repeatableDefinition = repeatableHandler.definition(getSchemaChangeLogTableName());
				final Table repeatableExisting = handler.getTable(connection, dialect, repeatableDefinition);
				final var appliedRepeatables = repeatableExisting == null ? java.util.Map.<String, String>of()
						: repeatableHandler.load(connection, dialect, repeatableExisting);
				for (final var repeatable : repeatableFiles) {
					final String previous = appliedRepeatables.get(repeatable.name());
					if (!repeatable.checksum().equals(previous)) {
						repeatablePending.add(new MigrationPlan.RepeatableEntry(repeatable.name(),
								repeatable.source().getAbsolutePath(), repeatable.statements().size(),
								!getNoTransactionFileFilter().test(repeatable.source()), repeatable.checksum(), previous));
					}
				}
			}
			plan = new MigrationPlan(existing != null, current, target,
					read(dialect, getSetupSqlDirectory()).size(), read(dialect, getFinalizeSqlDirectory()).size(),
					pending, issues, validation, drift, outOfOrder, isRejectOutOfOrder(), isRejectNonTransactional(),
					isRequireDownMigration(), databaseIdentity(connection), repeatablePending);
			if (outputFile != null) {
				new MigrationPlanIO().write(outputFile.toPath(), plan);
			}
			if (dryRunOutputFile != null) {
				writeDryRun(dryRunOutputFile, dialect, files, repeatableFiles, plan);
			}
			info(plan);
			if (failOnBlockers && plan.hasBlockers()) {
				throw new CommandException("Migration plan contains blockers: " + blockerSummary(plan));
			}
		});
	}

	private void writeDryRun(final File destination, final com.sqlapp.data.db.dialect.Dialect dialect,
			final List<DbVersionFileHandler.SqlFile> files, final List<RepeatableMigrationFile> repeatableFiles,
			final MigrationPlan migrationPlan) {
		final var selected = new java.util.HashSet<Long>();
		migrationPlan.pending().forEach(entry -> selected.add(entry.version()));
		final StringBuilder sql = new StringBuilder();
		sql.append("-- sqlapp migration dry run; review artifact only\n");
		appendSection(sql, "setup", read(dialect, getSetupSqlDirectory()));
		for (final var file : files) {
			if (!selected.contains(file.getVersionNumber())) {
				continue;
			}
			final String name = file.getUpSqlFile() == null ? "" : file.getUpSqlFile().getName()
					.replace('\r', '_').replace('\n', '_');
			sql.append("\n-- migration ").append(file.getVersionNumber()).append(": ").append(name)
					.append(isNoTransactionFile(file) ? " [non-transactional]" : " [transactional]").append('\n');
			appendStatements(sql, file.getUpSqls());
		}
		final var selectedRepeatables = migrationPlan.pendingRepeatables().stream()
				.map(MigrationPlan.RepeatableEntry::name).collect(java.util.stream.Collectors.toSet());
		for (final var repeatable : repeatableFiles) {
			if (!selectedRepeatables.contains(repeatable.name())) {
				continue;
			}
			sql.append("\n-- repeatable ").append(repeatable.name())
					.append(getNoTransactionFileFilter().test(repeatable.source())
							? " [non-transactional]" : " [transactional]").append('\n');
			appendStatements(sql, repeatable.statements());
		}
		appendSection(sql, "finalize", read(dialect, getFinalizeSqlDirectory()));
		final var absolute = destination.toPath().toAbsolutePath().normalize();
		try {
			AtomicMigrationFile.write(absolute,
					temporary -> Files.writeString(temporary, sql.toString(), StandardCharsets.UTF_8));
		} catch (final IOException | RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to write migration dry-run SQL: " + absolute, e);
		}
	}

	private boolean isNoTransactionFile(final DbVersionFileHandler.SqlFile file) {
		return file.getUpSqlFile() != null && getNoTransactionFileFilter().test(file.getUpSqlFile());
	}

	private static void appendSection(final StringBuilder sql, final String name,
			final List<SplitResult> statements) {
		if (statements == null || statements.isEmpty()) {
			return;
		}
		sql.append("\n-- ").append(name).append('\n');
		appendStatements(sql, statements);
	}

	private static void appendStatements(final StringBuilder sql, final List<SplitResult> statements) {
		for (final SplitResult statement : statements) {
			final String text = statement.getText().stripTrailing();
			sql.append(text);
			if (!text.endsWith(";")) {
				sql.append(';');
			}
			sql.append('\n');
		}
	}

	static MigrationPlan.DatabaseIdentity databaseIdentity(final java.sql.Connection connection)
			throws java.sql.SQLException {
		final var metadata = connection.getMetaData();
		final String location = String.valueOf(metadata.getURL()) + "\n" + String.valueOf(connection.getCatalog())
				+ "\n" + String.valueOf(connection.getSchema());
		try {
			final String fingerprint = "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(location.getBytes(StandardCharsets.UTF_8)));
			return new MigrationPlan.DatabaseIdentity(metadata.getDatabaseProductName(),
					metadata.getDatabaseProductVersion(), fingerprint);
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String blockerSummary(final MigrationPlan plan) {
		final var blockers = new ArrayList<String>();
		if (!plan.historyIssues().isEmpty()) {
			blockers.add("historyIssues=" + plan.historyIssues().size());
		}
		if (plan.checksumValidation() != null && plan.checksumValidation().hasFailures()) {
			final long failures = plan.checksumValidation().entries().stream()
					.filter(entry -> entry.state() == MigrationValidationResult.State.MISSING
							|| entry.state() == MigrationValidationResult.State.CHANGED)
					.count();
			blockers.add("checksumFailures=" + failures);
		}
		if (plan.schemaDrift() != null && !plan.schemaDrift().isCompatible()) {
			blockers.add("schemaDrift=" + plan.schemaDrift().compatibility());
		}
		if (plan.outOfOrderRejected() && !plan.outOfOrderVersions().isEmpty()) {
			blockers.add("outOfOrder=" + plan.outOfOrderVersions());
		}
		if (plan.nonTransactionalRejected() && (plan.pending().stream().anyMatch(entry -> !entry.transactional())
				|| plan.pendingRepeatables().stream().anyMatch(entry -> !entry.transactional()))) {
			blockers.add("nonTransactional=true");
		}
		if (plan.downMigrationRequired() && plan.pending().stream().anyMatch(entry -> !entry.rollbackAvailable())) {
			blockers.add("missingDownMigration=true");
		}
		return String.join(", ", blockers);
	}
}
