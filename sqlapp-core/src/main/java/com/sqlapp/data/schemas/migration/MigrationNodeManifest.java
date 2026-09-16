/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable node-level state used for selective migration execution. */
public record MigrationNodeManifest(int formatVersion, String planFingerprint, Map<String, Node> nodes) {

	public static final int CURRENT_VERSION = 1;

	public MigrationNodeManifest {
		if (formatVersion != CURRENT_VERSION) {
			throw new IllegalArgumentException("Unsupported migration node manifest version: " + formatVersion);
		}
		final Map<String, Node> copy = new LinkedHashMap<>();
		for (final var entry : nodes.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null
					|| !entry.getKey().equals(entry.getValue().id())) {
				throw new IllegalArgumentException("Migration node manifest keys must match node ids");
			}
			copy.put(entry.getKey(), entry.getValue());
		}
		nodes = Map.copyOf(copy);
	}

	public record Node(String id, String fingerprint, List<String> dependencies) {
		public Node {
			if (id == null || id.isBlank() || fingerprint == null || fingerprint.isBlank()) {
				throw new IllegalArgumentException("Migration node id and fingerprint are required");
			}
			dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
		}
	}
}
