/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.util.Objects;

/** Versioned machine-readable wrapper for a migration plan. */
public record MigrationPlanArtifact(int formatVersion, MigrationPlan plan) {
	public static final int CURRENT_FORMAT_VERSION = 2;

	public MigrationPlanArtifact {
		if (formatVersion != CURRENT_FORMAT_VERSION) {
			throw new IllegalArgumentException("Unsupported migration plan formatVersion: " + formatVersion);
		}
		Objects.requireNonNull(plan, "plan");
	}

	public static MigrationPlanArtifact of(final MigrationPlan plan) {
		return new MigrationPlanArtifact(CURRENT_FORMAT_VERSION, plan);
	}
}
