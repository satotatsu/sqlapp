/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.util.List;

/** Differences between one baseline migration snapshot and other environments. */
public record MigrationEnvironmentComparison(String baselineEnvironmentId, List<Environment> environments,
		List<Difference> differences) {
	public MigrationEnvironmentComparison {
		environments = List.copyOf(environments);
		differences = List.copyOf(differences);
	}

	public record Environment(String environmentId, String snapshotFingerprint,
			MigrationPlan.DatabaseIdentity databaseIdentity) {
	}

	public record Difference(String environmentId, Category category, String migration,
			String baselineValue, String environmentValue) {
	}

	public enum Category {
		DATABASE_PRODUCT, VERSIONED, REPEATABLE
	}

	public boolean matches() {
		return differences.isEmpty();
	}
}
