/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.migration.MigrationNodeManifest;

class MigrationNodeManifestIOTest {

	@TempDir
	Path directory;

	@Test
	void atomicallyRoundTripsManifest() {
		final var nodes = new LinkedHashMap<String, MigrationNodeManifest.Node>();
		nodes.put("parent", new MigrationNodeManifest.Node("parent", "p1", List.of()));
		nodes.put("child", new MigrationNodeManifest.Node("child", "c1", List.of("parent")));
		final var manifest = new MigrationNodeManifest(MigrationNodeManifest.CURRENT_VERSION, "plan-1", nodes);
		final Path file = directory.resolve("manifest.json");

		new MigrationNodeManifestIO().write(file, manifest);

		assertEquals(manifest, new MigrationNodeManifestIO().read(file));
	}
}
