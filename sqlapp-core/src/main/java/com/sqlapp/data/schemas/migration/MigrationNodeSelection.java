/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import java.util.Set;

/** State comparison and dependency-closed node selection. */
public record MigrationNodeSelection(Set<String> added, Set<String> modified, Set<String> removed,
		Set<String> selected) {
	public MigrationNodeSelection {
		added = Set.copyOf(added);
		modified = Set.copyOf(modified);
		removed = Set.copyOf(removed);
		selected = Set.copyOf(selected);
	}
}
