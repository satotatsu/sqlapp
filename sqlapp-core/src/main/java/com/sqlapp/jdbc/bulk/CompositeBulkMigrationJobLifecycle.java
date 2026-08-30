/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Combines independent maintenance lifecycles around one migration job. */
public final class CompositeBulkMigrationJobLifecycle
		implements BulkMigrationJobLifecycle {
	private final List<BulkMigrationJobLifecycle> components;

	public CompositeBulkMigrationJobLifecycle(
			final List<? extends BulkMigrationJobLifecycle> components) {
		Objects.requireNonNull(components, "components");
		this.components = List.copyOf(components);
		this.components.forEach(component -> Objects.requireNonNull(component,
				"lifecycle component"));
	}

	public static CompositeBulkMigrationJobLifecycle of(
			final BulkMigrationJobLifecycle... components) {
		return new CompositeBulkMigrationJobLifecycle(List.of(components));
	}

	public List<BulkMigrationJobLifecycle> getComponents() {
		return components;
	}

	@Override
	public String getConfigurationFingerprint() {
		return components.stream()
				.map(BulkMigrationJobLifecycle::getConfigurationFingerprint)
				.map(value -> value == null ? "<null>" : value)
				.map(value -> value.length() + ":" + value)
				.reduce("composite-v1", (left, right) -> left + right);
	}

	@Override
	public List<BulkMigrationJobOperation> plan(
			final List<BulkMigrationJobTask> tasks) {
		final List<BulkMigrationJobOperation> result = new ArrayList<>();
		final HashSet<String> ids = new HashSet<>();
		for (final BulkMigrationJobLifecycle component : components) {
			final List<BulkMigrationJobOperation> operations = Objects.requireNonNull(
					component.plan(tasks), "lifecycle operations");
			for (final BulkMigrationJobOperation operation : operations) {
				Objects.requireNonNull(operation, "lifecycle operation");
				if (!ids.add(operation.id())) {
					throw new IllegalArgumentException(
							"Duplicate migration lifecycle operation ID: " + operation.id());
				}
				result.add(operation);
			}
		}
		return List.copyOf(result);
	}

	@Override
	public void before(final Connection connection, final BulkMigrationJobPlan plan)
			throws SQLException {
		for (final BulkMigrationJobLifecycle component : components) {
			component.before(connection, plan);
		}
	}

	@Override
	public void after(final Connection connection, final BulkMigrationJobPlan plan,
			final BulkMigrationJobResult result) throws SQLException {
		for (final BulkMigrationJobLifecycle component : components) {
			component.after(connection, plan, result);
		}
	}

	@Override
	public void restore(final Connection connection, final BulkMigrationJobPlan plan,
			final Throwable failure) throws SQLException {
		Throwable first = null;
		for (int i = components.size() - 1; i >= 0; i--) {
			try {
				components.get(i).restore(connection, plan, failure);
			} catch (SQLException | RuntimeException | Error e) {
				if (first == null) {
					first = e;
				} else {
					first.addSuppressed(e);
				}
			}
		}
		if (first instanceof SQLException e) {
			throw e;
		}
		if (first instanceof RuntimeException e) {
			throw e;
		}
		if (first instanceof Error e) {
			throw e;
		}
	}
}
