/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.migration.MigrationNodeManifest;
import com.sqlapp.exceptions.CommandException;

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

		final var io = new MigrationNodeManifestIO();
		final var written = io.writeSnapshot(file, manifest, 1_000_000L);

		assertEquals(manifest, written.manifest());
		assertEquals("sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(file.toFile()),
				written.fingerprint());
		assertEquals(written, io.readSnapshot(file, 1_000_000L));
		assertThrows(CommandException.class, () -> io.read(file, 1L));
	}
}
