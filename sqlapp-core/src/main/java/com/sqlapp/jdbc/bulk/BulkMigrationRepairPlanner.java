/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import com.sqlapp.data.schemas.Table;

/** Creates validated repair plans without reading or changing source data. */
public final class BulkMigrationRepairPlanner {
	private BulkMigrationRepairPlanner() {
	}

	public static BulkMigrationRepairPlan plan(final Connection targetConnection,
			final Table expected, final BulkMigrationVerificationResult verification,
			final BulkMigrationRepairOption options) throws SQLException {
		return plan(targetConnection, expected, expected, verification, options);
	}

	public static BulkMigrationRepairPlan plan(final Connection targetConnection,
			final Table expected, final Table target,
			final BulkMigrationVerificationResult verification,
			final BulkMigrationRepairOption options) throws SQLException {
		return create(targetConnection, Objects.requireNonNull(expected, "expected"), null,
				Objects.requireNonNull(target, "target"), verification, options);
	}

	public static BulkMigrationRepairPlan plan(final Connection targetConnection,
			final BulkMigrationKeysetSource expected,
			final BulkMigrationVerificationResult verification,
			final BulkMigrationRepairOption options) throws SQLException {
		return plan(targetConnection, expected, expected.getTable(), verification, options);
	}

	public static BulkMigrationRepairPlan plan(final Connection targetConnection,
			final BulkMigrationKeysetSource expected, final Table target,
			final BulkMigrationVerificationResult verification,
			final BulkMigrationRepairOption options) throws SQLException {
		Objects.requireNonNull(expected, "expected");
		BulkMigrationRepairExecutor.validateKeysetSource(expected, verification);
		return create(targetConnection, Objects.requireNonNull(expected.getTable(),
				"expected.table"), expected, Objects.requireNonNull(target, "target"),
				verification, options);
	}

	private static BulkMigrationRepairPlan create(final Connection targetConnection,
			final Table expected, final BulkMigrationKeysetSource expectedKeysetSource,
			final Table target, final BulkMigrationVerificationResult verification,
			final BulkMigrationRepairOption options) throws SQLException {
		Objects.requireNonNull(targetConnection, "targetConnection");
		Objects.requireNonNull(verification, "verification");
		Objects.requireNonNull(options, "options");
		BulkMigrationRepairExecutor.validateConfiguration(expected, target, verification,
				options);
		final boolean hasReplay = verification.getMismatches().stream()
				.anyMatch(chunk -> chunk.getExpectedRows() > 0);
		final BulkUpsertOption configured = options.getBulkUpsertOption() == null
				? BulkUpsertOption.defaults() : options.getBulkUpsertOption();
		final BulkUpsertOption effective = configured.getKeyColumns().isEmpty()
				? ChunkedBulkMigrationExecutor.copyWithKeys(configured,
						ChunkedBulkMigrationExecutor.primaryKeyNames(target))
				: configured;
		final BulkUpsertPlan upsertPlan = hasReplay
				? BulkUpsertPlan.resolve(target, effective) : null;
		if (!hasReplay) {
			return new BulkMigrationRepairPlan(expected, expectedKeysetSource, target,
					verification, options, null, false, true, "<not-required>",
					"<not-required>", "<none>");
		}
		final BulkUpsertExecutor executor = BulkUpsertResolver.resolve(targetConnection);
		final boolean transactionBreakingStaging =
				!executor.supportsCallerTransactionAtomicity();
		final boolean atomic = effective.isUseTransaction() && !transactionBreakingStaging;
		if (effective.isUseTransaction() && !atomic
				&& verification.getMismatches().stream()
						.anyMatch(chunk -> chunk.getExpectedRows() > 0)) {
			throw new IllegalStateException("The selected bulk upsert provider uses "
					+ "transaction-breaking staging DDL and cannot atomically repair all chunks; "
					+ "set bulkUpsertOption.useTransaction=false to allow non-atomic repair");
		}
		final var metadata = targetConnection.getMetaData();
		return new BulkMigrationRepairPlan(expected, expectedKeysetSource, target,
				verification, options, upsertPlan, transactionBreakingStaging, atomic,
				metadata.getDatabaseProductName(), metadata.getDatabaseProductVersion(),
				executor.getClass().getName());
	}

	static void validateExecutionConnection(final Connection connection,
			final BulkMigrationRepairPlan plan) throws SQLException {
		if (plan.isNoOp() || plan.getEstimatedReplayRows() == 0) {
			return;
		}
		final var metadata = connection.getMetaData();
		final String executor = BulkUpsertResolver.resolve(connection).getClass().getName();
		if (!plan.getDatabaseProductName().equals(metadata.getDatabaseProductName())
				|| !plan.getDatabaseProductVersion().equals(metadata.getDatabaseProductVersion())
				|| !plan.getExecutorClassName().equals(executor)) {
			throw new IllegalArgumentException(
					"Target connection differs from the repair plan database");
		}
	}
}
