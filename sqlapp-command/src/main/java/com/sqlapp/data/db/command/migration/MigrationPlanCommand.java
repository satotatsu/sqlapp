/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CommandException;

import lombok.Getter;
import lombok.Setter;

/** Builds a migration plan without creating/upgrading history or executing SQL. */
@Getter
@Setter
public class MigrationPlanCommand extends MigrationCommand {
	/** Optional JSON artifact destination. */
	private File outputFile;
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
			plan = new MigrationPlan(existing != null, current, target,
					read(dialect, getSetupSqlDirectory()).size(), read(dialect, getFinalizeSqlDirectory()).size(),
					pending, issues, validation, drift, outOfOrder, isRejectOutOfOrder(), isRejectNonTransactional(),
					isRequireDownMigration());
			if (outputFile != null) {
				new MigrationPlanIO().write(outputFile.toPath(), plan);
			}
			info(plan);
			if (failOnBlockers && plan.hasBlockers()) {
				throw new CommandException("Migration plan contains blockers: " + blockerSummary(plan));
			}
		});
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
		if (plan.nonTransactionalRejected() && plan.pending().stream().anyMatch(entry -> !entry.transactional())) {
			blockers.add("nonTransactional=true");
		}
		if (plan.downMigrationRequired() && plan.pending().stream().anyMatch(entry -> !entry.rollbackAvailable())) {
			blockers.add("missingDownMigration=true");
		}
		return String.join(", ", blockers);
	}
}
