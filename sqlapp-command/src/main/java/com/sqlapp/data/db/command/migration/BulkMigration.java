/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationJobExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlanner;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairPlan;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairPlanner;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairTask;
import com.sqlapp.jdbc.bulk.BulkMigrationJobResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobStatus;
import com.sqlapp.jdbc.bulk.BulkMigrationJobStatusInspector;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTask;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskVerificationResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobVerificationResult;
import com.sqlapp.jdbc.bulk.BulkMigrationMode;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairOption;
import com.sqlapp.jdbc.bulk.BulkMigrationVerifier;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationOption;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationKeysetSource;

import lombok.Builder;

/**
 * Simple, safe facade for the usual execute, inspect, verify, and repair flow.
 * Advanced callers can continue to use the underlying bulk APIs directly.
 */
public final class BulkMigration {
	private final DataSource source;
	private final DataSource target;
	private final List<Table> tables;
	private final BulkMigrationMode mode;
	private final int chunkSize;
	private final boolean resume;
	private final String sourceFingerprint;
	private final String targetFingerprint;
	private final String checkpointTableName;
	private final BulkUpsertOption upsertOption;

	@Builder
	private BulkMigration(final DataSource source, final DataSource target,
			final Schema schema, final List<String> tableNames,
			final BulkMigrationMode mode, final Integer chunkSize,
			final Boolean resume, final String sourceFingerprint,
			final String targetFingerprint, final String checkpointTableName,
			final BulkUpsertOption upsertOption) {
		this.source = Objects.requireNonNull(source, "source");
		this.target = Objects.requireNonNull(target, "target");
		this.tables = resolveTables(Objects.requireNonNull(schema, "schema"), tableNames);
		this.mode = mode == null ? BulkMigrationMode.UPSERT : mode;
		this.chunkSize = chunkSize == null ? 10_000 : chunkSize;
		this.resume = resume != null && resume;
		this.sourceFingerprint = sourceFingerprint;
		this.targetFingerprint = targetFingerprint;
		this.checkpointTableName = checkpointTableName == null
				|| checkpointTableName.isBlank() ? "SQLAPP_BULK_MIGRATION_CHECKPOINT"
						: checkpointTableName;
		this.upsertOption = upsertOption == null ? BulkUpsertOption.defaults() : upsertOption;
		validate();
	}

	/** Convenience aliases for the common builder inputs. */
	public static class BulkMigrationBuilder {
		public BulkMigrationBuilder tables(final String... names) {
			this.tableNames = names == null ? null : List.of(names);
			return this;
		}

		public BulkMigrationBuilder fingerprints(final String source,
				final String target) {
			this.sourceFingerprint = source;
			this.targetFingerprint = target;
			return this;
		}
	}

	public BulkMigrationJobResult execute() throws SQLException {
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection()) {
			return BulkMigrationJobExecutor.executePlan(targetConnection,
					plan(sourceConnection, targetConnection));
		}
	}

	public BulkMigrationJobStatus inspect() throws SQLException {
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection()) {
			return BulkMigrationJobStatusInspector.inspect(
					plan(sourceConnection, targetConnection));
		}
	}

	public BulkMigrationJobVerificationResult verify() throws SQLException {
		try (Connection sourceConnection = source.getConnection();
				Connection targetConnection = target.getConnection()) {
			final List<BulkMigrationJobTaskVerificationResult> results = new ArrayList<>();
			for (final Table table : orderedTables()) {
				final var expected = new JdbcBulkMigrationKeysetSource(sourceConnection, table);
				final var actual = new JdbcBulkMigrationKeysetSource(targetConnection, table);
				final List<String> columns = table.getColumns().stream()
						.map(column -> column.getName()).toList();
				final var verification = BulkMigrationVerifier.verify(expected, actual,
						columns, chunkSize);
				results.add(new BulkMigrationJobTaskVerificationResult(taskId(table),
						columns, verification));
			}
			return new BulkMigrationJobVerificationResult(results);
		}
	}

	public Repair planRepair(final BulkMigrationJobVerificationResult verification) {
		return new Repair(this, Objects.requireNonNull(verification, "verification"));
	}

	private BulkMigrationJobPlan plan(final Connection sourceConnection,
			final Connection targetConnection) throws SQLException {
		final List<BulkMigrationJobTask> tasks = new ArrayList<>();
		for (final Table table : tables) {
			final var options = options(table);
			tasks.add(BulkMigrationJobTask.builder().taskId(taskId(table))
					.keysetSource(new JdbcBulkMigrationKeysetSource(sourceConnection, table))
					.options(options).checkpointStore(new JdbcBulkMigrationCheckpointStore(
							targetConnection, checkpointTableName)).build());
		}
		return BulkMigrationJobPlanner.plan(tasks);
	}

	private BulkMigrationJobRepairPlan repairPlan(final Connection sourceConnection,
			final Connection targetConnection,
			final BulkMigrationJobVerificationResult verification) throws SQLException {
		if (!verification.getTasks().stream().map(
				BulkMigrationJobTaskVerificationResult::getTaskId).toList()
				.equals(orderedTables().stream().map(BulkMigration::taskId).toList())) {
			throw new IllegalArgumentException(
					"Verification tasks do not match the migration tables and dependency order");
		}
		final List<BulkMigrationJobRepairTask> tasks = new ArrayList<>();
		for (int i = 0; i < verification.getTasks().size(); i++) {
			final Table table = orderedTables().get(i);
			final var verified = verification.getTasks().get(i);
			tasks.add(BulkMigrationJobRepairTask.builder().taskId(taskId(table))
					.expectedKeysetSource(new JdbcBulkMigrationKeysetSource(
							sourceConnection, table)).target(table)
					.verificationResult(verified.getVerificationResult())
					.options(BulkMigrationRepairOption.builder()
							.bulkUpsertOption(upsertOption).build()).build());
		}
		return BulkMigrationJobRepairPlanner.plan(targetConnection, tasks);
	}

	private ChunkedBulkMigrationOption options(final Table table) {
		return ChunkedBulkMigrationOption.builder().migrationId(taskId(table))
				.chunkSize(chunkSize).mode(mode).resume(resume)
				.checkpointTableName(checkpointTableName)
				.sourceFingerprint(sourceFingerprint).targetFingerprint(targetFingerprint)
				.bulkUpsertOption(upsertOption).build();
	}

	private List<Table> orderedTables() {
		return Table.TableOrder.CREATE.sort(tables, table -> table);
	}

	private void validate() {
		if (tables.isEmpty()) {
			throw new IllegalArgumentException("At least one migration table is required");
		}
		if (chunkSize <= 0) {
			throw new IllegalArgumentException("chunkSize must be greater than zero");
		}
		if (resume && (blank(sourceFingerprint) || blank(targetFingerprint))) {
			throw new IllegalArgumentException(
					"sourceFingerprint and targetFingerprint are required when resume is enabled");
		}
	}

	private static List<Table> resolveTables(final Schema schema,
			final List<String> names) {
		if (names == null || names.isEmpty()) {
			return List.copyOf(schema.getTables());
		}
		final var unique = new HashSet<String>();
		final List<Table> result = new ArrayList<>();
		for (final String name : names) {
			if (name == null || name.isBlank() || !unique.add(name)) {
				throw new IllegalArgumentException("Table names must be non-empty and unique");
			}
			final Table table = schema.getTables().get(name);
			if (table == null) {
				throw new IllegalArgumentException("Unknown migration table: " + name);
			}
			result.add(table);
		}
		return List.copyOf(result);
	}

	private static String taskId(final Table table) {
		return table.getSchemaName() == null ? table.getName()
				: table.getSchemaName() + "." + table.getName();
	}

	private static boolean blank(final String value) {
		return value == null || value.isBlank();
	}

	/** Reviewed repair flow bound to this migration configuration. */
	public static final class Repair {
		private final BulkMigration migration;
		private final BulkMigrationJobVerificationResult verification;

		private Repair(final BulkMigration migration,
				final BulkMigrationJobVerificationResult verification) {
			this.migration = migration;
			this.verification = verification;
		}

		public BulkMigrationJobRepairPlanReport writeJson(final Path file)
				throws SQLException {
			try (Connection sourceConnection = migration.source.getConnection();
					Connection targetConnection = migration.target.getConnection()) {
				final var plan = migration.repairPlan(sourceConnection, targetConnection,
						verification);
				final var io = new BulkMigrationJobRepairPlanReportIO();
				final var report = io.fromPlan(plan);
				io.write(file, report);
				return report;
			}
		}

		public BulkMigrationJobRepairResult executeApproved(
				final String approvedFingerprint) throws SQLException {
			try (Connection sourceConnection = migration.source.getConnection();
					Connection targetConnection = migration.target.getConnection()) {
				final var plan = migration.repairPlan(sourceConnection, targetConnection,
						verification);
				return BulkMigrationJobRepairExecutor.execute(targetConnection, plan,
						approvedFingerprint);
			}
		}
	}
}
