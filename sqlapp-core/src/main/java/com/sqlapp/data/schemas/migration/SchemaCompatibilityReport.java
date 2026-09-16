/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import java.util.List;

/** Result of comparing a migration's expected schema with an actual schema. */
public record SchemaCompatibilityReport(SchemaCompatibility compatibility,
		List<SchemaCompatibilityChange> changes) {

	public SchemaCompatibilityReport {
		changes = List.copyOf(changes);
	}

	public boolean isCompatible() {
		return compatibility != SchemaCompatibility.BREAKING;
	}
}
