/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class MigrationNodeStateSelectorTest {

	@Test
	void selectsModifiedNodesAndTransitiveDownstream() {
		final var previous = manifest(Map.of("A", node("A", "a1"), "B", node("B", "b1", "A"), "C",
				node("C", "c1", "B"), "REMOVED", node("REMOVED", "r1")));
		final Map<String, MigrationNodeManifest.Node> currentNodes = new LinkedHashMap<>();
		currentNodes.put("A", node("A", "a2"));
		currentNodes.put("B", node("B", "b1", "A"));
		currentNodes.put("C", node("C", "c1", "B"));
		currentNodes.put("NEW", node("NEW", "n1"));

		final var selection = MigrationNodeStateSelector.modifiedAndDownstream(previous, manifest(currentNodes));

		assertEquals(Set.of("A"), selection.modified());
		assertEquals(Set.of("NEW"), selection.added());
		assertEquals(Set.of("REMOVED"), selection.removed());
		assertEquals(Set.of("A", "B", "C", "NEW"), selection.selected());
	}

	private static MigrationNodeManifest manifest(final Map<String, MigrationNodeManifest.Node> nodes) {
		return new MigrationNodeManifest(MigrationNodeManifest.CURRENT_VERSION, "plan", nodes);
	}

	private static MigrationNodeManifest.Node node(final String id, final String fingerprint,
			final String... dependencies) {
		return new MigrationNodeManifest.Node(id, fingerprint, List.of(dependencies));
	}
}
