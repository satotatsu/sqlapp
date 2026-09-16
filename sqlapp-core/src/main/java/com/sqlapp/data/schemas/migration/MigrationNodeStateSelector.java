/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Selects added/modified migration nodes and every transitive downstream node. */
public final class MigrationNodeStateSelector {

	private MigrationNodeStateSelector() {
	}

	public static MigrationNodeSelection modifiedAndDownstream(final MigrationNodeManifest previous,
			final MigrationNodeManifest current) {
		Objects.requireNonNull(previous, "previous");
		Objects.requireNonNull(current, "current");
		final Set<String> added = new LinkedHashSet<>();
		final Set<String> modified = new LinkedHashSet<>();
		for (final var entry : current.nodes().entrySet()) {
			final var old = previous.nodes().get(entry.getKey());
			if (old == null) {
				added.add(entry.getKey());
			} else if (!old.fingerprint().equals(entry.getValue().fingerprint())
					|| !old.dependencies().equals(entry.getValue().dependencies())) {
				modified.add(entry.getKey());
			}
		}
		final Set<String> removed = new LinkedHashSet<>(previous.nodes().keySet());
		removed.removeAll(current.nodes().keySet());
		final Set<String> selected = new LinkedHashSet<>(added);
		selected.addAll(modified);
		final ArrayDeque<String> queue = new ArrayDeque<>(selected);
		while (!queue.isEmpty()) {
			final String changed = queue.removeFirst();
			for (final var node : current.nodes().values()) {
				if (node.dependencies().contains(changed) && selected.add(node.id())) {
					queue.addLast(node.id());
				}
			}
		}
		return new MigrationNodeSelection(added, modified, removed, selected);
	}
}
