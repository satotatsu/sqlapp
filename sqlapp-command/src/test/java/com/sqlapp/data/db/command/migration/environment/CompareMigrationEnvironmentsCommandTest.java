/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.environment;

import com.sqlapp.data.db.command.migration.schema.MigrationPlan;
import com.sqlapp.data.db.command.migration.schema.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.exceptions.CommandException;

class CompareMigrationEnvironmentsCommandTest {
	@TempDir
	Path directory;

	private MigrationEnvironmentSnapshot snapshot(final String environment, final String productVersion,
			final String versionedChecksum, final String repeatableChecksum) {
		return new MigrationEnvironmentSnapshot(environment, 1_800_000_000_000L,
				new MigrationPlan.DatabaseIdentity("HSQL Database Engine", productVersion,
						"sha256:" + environment.substring(0, 1).repeat(64)),
				List.of(new MigrationEnvironmentSnapshot.VersionedEntry(1, Status.Completed, versionedChecksum)),
				List.of(new MigrationEnvironmentSnapshot.RepeatableEntry("view", repeatableChecksum)));
	}

	@Test
	void comparesEveryEnvironmentAgainstExplicitBaselineAndCanFailCi() {
		final Path production = directory.resolve("production.json");
		final Path staging = directory.resolve("staging.json");
		final var io = new MigrationEnvironmentSnapshotIO();
		io.write(production, snapshot("production", "2.7.4", "sha256:a", "sha256:b"));
		io.write(staging, snapshot("staging", "2.7.3", "sha256:c", "sha256:d"));
		final var command = new CompareMigrationEnvironmentsCommand();
		command.setSnapshotFiles(List.of(staging.toFile(), production.toFile()));
		command.setBaselineEnvironmentId("production");
		command.setOutputFile(directory.resolve("comparison.json").toFile());
		command.run();
		assertEquals("production", command.getComparison().baselineEnvironmentId());
		assertFalse(command.getComparison().matches());
		assertEquals(List.of(MigrationEnvironmentComparison.Category.DATABASE_PRODUCT,
				MigrationEnvironmentComparison.Category.VERSIONED,
				MigrationEnvironmentComparison.Category.REPEATABLE),
				command.getComparison().differences().stream()
						.map(MigrationEnvironmentComparison.Difference::category).toList());
		final var gate = new CompareMigrationEnvironmentsCommand();
		gate.setSnapshotFiles(List.of(production.toFile(), staging.toFile()));
		gate.setFailOnDifferences(true);
		assertThrows(CommandException.class, gate::run);
	}

	@Test
	void rejectsTamperedSnapshot() throws Exception {
		final Path file = directory.resolve("snapshot.json");
		final var io = new MigrationEnvironmentSnapshotIO();
		io.write(file, snapshot("production", "2.7.4", "sha256:a", "sha256:b"));
		Files.writeString(file, Files.readString(file).replace("production", "tampered"));
		assertThrows(CommandException.class, () -> io.read(file));
	}
}
