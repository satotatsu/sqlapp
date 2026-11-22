/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;
import com.sqlapp.jdbc.bulk.GeneratedKeyCounters;

/**
 * Raises target identity/serial generators after a verified copy that kept the
 * source keys, so later automatic inserts cannot collide with imported rows.
 */
final class TransferGeneratedKeySynchronization {
	private TransferGeneratedKeySynchronization() {
	}

	/**
	 * Whether the transfer path of this configuration synchronizes counters. Direct
	 * copies do; file imports and staged replacement keep the counter warning.
	 */
	static boolean synchronizes(final TransferJobConfiguration configuration) {
		return configuration.dataMode() != TransferJobConfiguration.DataMode.FILE
				&& configuration.dataMode() != TransferJobConfiguration.DataMode.SCHEMA_ONLY && configuration
						.effectiveTargetPreparation() != TransferJobConfiguration.TargetPreparation.STAGED_REPLACE;
	}

	static List<GeneratedKeyCounters.Result> synchronize(final TransferJobConfiguration configuration,
			final BulkMigrationJobPlan plan, final DataSource target) throws SQLException {
		if (plan == null || !synchronizes(configuration) || configuration
				.effectiveGeneratedKeyCounters() != TransferJobConfiguration.GeneratedKeyCounterMode.SYNCHRONIZE)
			return List.of();
		final boolean explicit = configuration.explicitGeneratedKeyCounters();
		final var results = new ArrayList<GeneratedKeyCounters.Result>();
		try (var connection = target.getConnection()) {
			if (!GeneratedKeyCounters.supported(DialectResolver.getInstance().getDialect(connection), explicit))
				return List.of();
			connection.setReadOnly(false);
			connection.setAutoCommit(true);
			for (var task : plan.getTasks()) {
				final var targetTable = task.getEffectiveTargetTable();
				for (var column : task.getEffectiveSourceTable().getColumns()) {
					if (!column.isIdentity()
							&& (column.getDataType() == null || !column.getDataType().isAutoIncrementable()))
						continue;
					final var targetColumn = targetTable.getColumns().get(task.getTargetColumnName(column.getName()));
					if (targetColumn == null)
						continue;
					final var keyed = targetColumn.clone();
					if (keyed.getIdentityStep() == null && column.getIdentityStep() != null)
						keyed.setIdentityStep(column.getIdentityStep());
					results.add(GeneratedKeyCounters.synchronize(connection, targetTable, keyed,
							column.getIdentityLastValue(), explicit));
				}
			}
		}
		return List.copyOf(results);
	}
}
