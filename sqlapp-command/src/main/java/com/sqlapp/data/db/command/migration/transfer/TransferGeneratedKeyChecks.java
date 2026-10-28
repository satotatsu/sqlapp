/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;

/**
 * Per-column follow-up diagnostics derived from the canonical selected Schema.
 */
public final class TransferGeneratedKeyChecks {
	/**
	 * Stable diagnostic code within the existing warning list; not a configuration
	 * option.
	 */
	public static final String CODE = "[GENERATED_KEY_COUNTER]";

	private TransferGeneratedKeyChecks() {
	}

	static List<String> required(BulkMigrationJobPlan plan) {
		final var result = new ArrayList<String>();
		for (var task : plan.getTasks()) {
			final var target = task.getEffectiveTargetTable();
			final var parts = new ArrayList<String>();
			for (String part : new String[] { target.getCatalogName(), target.getSchemaName(), target.getName() })
				if (part != null && !part.isBlank())
					parts.add(part);
			for (var column : task.getEffectiveSourceTable().getColumns()) {
				if (!column.isIdentity()
						&& (column.getDataType() == null || !column.getDataType().isAutoIncrementable()))
					continue;
				result.add(CODE + " Target " + printable(String.join(".", parts)) + ", column "
						+ printable(column.getName())
						+ ": source IDs are preserved, but the target generator counter is not checked or synchronized. "
						+ "Before automatic INSERTs, check that generated values cannot collide with imported keys. "
						+ "If the target column has no generator, this check is not applicable.");
			}
		}
		return List.copyOf(result);
	}

	/**
	 * Returns the generated-key diagnostics without their code, retaining warning
	 * order.
	 */
	public static List<String> messages(List<String> warnings) {
		return warnings.stream().filter(value -> value.startsWith(CODE + " "))
				.map(value -> value.substring(CODE.length() + 1)).toList();
	}

	private static String printable(String value) {
		return "'" + value.replaceAll("\\p{Cntrl}", " ").replace("'", "''") + "'";
	}
}
