/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.JsonConverter;

import lombok.Getter;
import lombok.Setter;

/** Compares migration-state snapshots without opening database connections. */
@Getter
@Setter
public class CompareMigrationEnvironmentsCommand extends AbstractCommand {
	private List<File> snapshotFiles = List.of();
	private String baselineEnvironmentId;
	private File outputFile;
	private boolean failOnDifferences;
	private MigrationEnvironmentComparison comparison;

	@Override
	protected void doRun() {
		if (snapshotFiles == null || snapshotFiles.size() < 2) {
			throw new CommandException("At least two migration environment snapshot files are required");
		}
		final var io = new MigrationEnvironmentSnapshotIO();
		final Map<String, MigrationEnvironmentSnapshot> snapshots = new LinkedHashMap<>();
		for (final File file : snapshotFiles) {
			if (file == null) {
				throw new CommandException("Migration environment snapshot file must not be null");
			}
			final var snapshot = io.read(file.toPath());
			if (snapshots.putIfAbsent(snapshot.environmentId(), snapshot) != null) {
				throw new CommandException("Duplicate environmentId in migration snapshots: " + snapshot.environmentId());
			}
		}
		if (baselineEnvironmentId != null && baselineEnvironmentId.isBlank()) {
			throw new CommandException("baselineEnvironmentId must not be blank");
		}
		final String baselineId = baselineEnvironmentId == null ? snapshots.keySet().iterator().next()
				: baselineEnvironmentId;
		final var baseline = snapshots.get(baselineId);
		if (baseline == null) {
			throw new CommandException("baselineEnvironmentId was not found in snapshots: " + baselineId);
		}
		final var environments = snapshots.values().stream()
				.map(snapshot -> new MigrationEnvironmentComparison.Environment(snapshot.environmentId(),
						snapshot.fingerprint(), snapshot.databaseIdentity()))
				.toList();
		final var differences = new ArrayList<MigrationEnvironmentComparison.Difference>();
		for (final var snapshot : snapshots.values()) {
			if (snapshot == baseline) {
				continue;
			}
			compareDatabase(baseline, snapshot, differences);
			compareVersioned(baseline, snapshot, differences);
			compareRepeatables(baseline, snapshot, differences);
		}
		comparison = new MigrationEnvironmentComparison(baselineId, environments, differences);
		if (outputFile != null) {
			write(outputFile, comparison);
		}
		info("Migration environments match: ", comparison.matches());
		if (failOnDifferences && !comparison.matches()) {
			throw new CommandException("Migration environments differ: " + comparison.differences());
		}
	}

	private void compareDatabase(final MigrationEnvironmentSnapshot baseline,
			final MigrationEnvironmentSnapshot actual,
			final List<MigrationEnvironmentComparison.Difference> differences) {
		final String expected = baseline.databaseIdentity() == null ? null
				: baseline.databaseIdentity().productName() + " " + baseline.databaseIdentity().productVersion();
		final String value = actual.databaseIdentity() == null ? null
				: actual.databaseIdentity().productName() + " " + actual.databaseIdentity().productVersion();
		addDifference(actual.environmentId(), MigrationEnvironmentComparison.Category.DATABASE_PRODUCT,
				"database", expected, value, differences);
	}

	private void compareVersioned(final MigrationEnvironmentSnapshot baseline,
			final MigrationEnvironmentSnapshot actual,
			final List<MigrationEnvironmentComparison.Difference> differences) {
		final Map<Long, String> expected = new LinkedHashMap<>();
		baseline.versioned().forEach(entry -> expected.put(entry.version(), entry.status() + "|" + entry.checksum()));
		final Map<Long, String> values = new LinkedHashMap<>();
		actual.versioned().forEach(entry -> values.put(entry.version(), entry.status() + "|" + entry.checksum()));
		for (final Long version : union(expected.keySet(), values.keySet())) {
			addDifference(actual.environmentId(), MigrationEnvironmentComparison.Category.VERSIONED,
					String.valueOf(version), expected.get(version), values.get(version), differences);
		}
	}

	private void compareRepeatables(final MigrationEnvironmentSnapshot baseline,
			final MigrationEnvironmentSnapshot actual,
			final List<MigrationEnvironmentComparison.Difference> differences) {
		final Map<String, String> expected = new LinkedHashMap<>();
		baseline.repeatables().forEach(entry -> expected.put(entry.name(), entry.checksum()));
		final Map<String, String> values = new LinkedHashMap<>();
		actual.repeatables().forEach(entry -> values.put(entry.name(), entry.checksum()));
		for (final String name : union(expected.keySet(), values.keySet())) {
			addDifference(actual.environmentId(), MigrationEnvironmentComparison.Category.REPEATABLE,
					name, expected.get(name), values.get(name), differences);
		}
	}

	private static <T extends Comparable<? super T>> TreeSet<T> union(final java.util.Set<T> left,
			final java.util.Set<T> right) {
		final TreeSet<T> result = new TreeSet<>(left);
		result.addAll(right);
		return result;
	}

	private void addDifference(final String environmentId, final MigrationEnvironmentComparison.Category category,
			final String migration, final String expected, final String actual,
			final List<MigrationEnvironmentComparison.Difference> differences) {
		if (!java.util.Objects.equals(expected, actual)) {
			differences.add(new MigrationEnvironmentComparison.Difference(environmentId, category, migration,
					expected, actual));
		}
	}

	private void write(final File destination, final MigrationEnvironmentComparison value) {
		try {
			final JsonConverter converter = new JsonConverter();
			converter.setIndentOutput(true);
			AtomicMigrationFile.write(destination.toPath().toAbsolutePath().normalize(),
					temporary -> converter.writeJsonValue(temporary.toFile(), value));
		} catch (final IOException | RuntimeException e) {
			throw e instanceof CommandException commandException ? commandException
					: new CommandException("Failed to write migration environment comparison: " + destination, e);
		}
	}
}
