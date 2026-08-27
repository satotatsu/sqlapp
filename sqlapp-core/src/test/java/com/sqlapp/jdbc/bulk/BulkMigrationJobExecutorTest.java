/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

class BulkMigrationJobExecutorTest {
	@Test
	void ordersKeysetTasksUsingTheirSchemaTables() {
		final Table parent = table("PARENT");
		final Table child = table("CHILD");
		child.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT",
				child.getColumns().get("ID"), parent.getColumns().get("ID"));
		final var parentTask = keysetTask("parent", "migration-parent", parent);
		final var childTask = keysetTask("child", "migration-child", child);

		final var ordered = BulkMigrationJobExecutor.order(List.of(childTask, parentTask));

		assertEquals(List.of("parent", "child"), ordered.stream()
				.map(BulkMigrationJobTask::getTaskId).toList());
	}

	@Test
	void publicPlannerReturnsAnImmutableValidatedDryRunOrder() {
		final Table parent = table("PARENT");
		final Table child = table("CHILD");
		child.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT",
				child.getColumns().get("ID"), parent.getColumns().get("ID"));
		final var parentTask = tableTask("parent", "plan-parent", parent);
		final var childTask = tableTask("child", "plan-child", child);

		final var plan = BulkMigrationJobPlanner.plan(List.of(childTask, parentTask));

		assertEquals(List.of("parent", "child"), plan.getTaskIds());
		assertThrows(UnsupportedOperationException.class,
				() -> plan.getTasks().add(parentTask));
		assertEquals(plan.getFingerprint(), BulkMigrationJobPlanner
				.plan(List.of(parentTask, childTask)).getFingerprint());
		final var changedChild = tableTask("child", "plan-child", child,
				ChunkedBulkMigrationOption.builder().migrationId("plan-child")
						.chunkSize(123).build());
		assertNotEquals(plan.getFingerprint(),
				BulkMigrationJobPlanner.plan(List.of(changedChild, parentTask)).getFingerprint());
	}

	@Test
	void rejectsDuplicateTaskIds() {
		final var first = tableTask("duplicate", "migration-1", table("A"));
		final var second = tableTask("duplicate", "migration-2", table("B"));

		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobExecutor.order(List.of(first, second)));
	}

	@Test
	void rejectsDuplicateCheckpointMigrationIds() {
		final var first = tableTask("first", "duplicate", table("A"));
		final var second = tableTask("second", "duplicate", table("B"));

		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobExecutor.order(List.of(first, second)));
	}

	@Test
	void requiresExactlyOneSourceKind() {
		final Table table = table("TARGET");
		final var missing = BulkMigrationJobTask.builder().taskId("missing")
				.options(options("missing-source")).build();
		final var both = BulkMigrationJobTask.builder().taskId("both")
				.sourceTable(table).keysetSource(keyset(table))
				.options(options("both-sources")).build();

		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobExecutor.order(List.of(missing)));
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobExecutor.order(List.of(both)));
	}

	@Test
	void requiresNonBlankTaskAndMigrationIds() {
		final var missingTaskId = tableTask(" ", "migration", table("A"));
		final var missingMigrationId = tableTask("task", " ", table("B"));

		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobExecutor.order(List.of(missingTaskId)));
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobExecutor.order(List.of(missingMigrationId)));
	}

	@Test
	void rejectsCyclicForeignKeyDependencies() {
		final Table firstTable = table("FIRST");
		final Table secondTable = table("SECOND");
		firstTable.getConstraints().addForeignKeyConstraint("FK_FIRST_SECOND",
				firstTable.getColumns().get("ID"), secondTable.getColumns().get("ID"));
		secondTable.getConstraints().addForeignKeyConstraint("FK_SECOND_FIRST",
				secondTable.getColumns().get("ID"), firstTable.getColumns().get("ID"));
		final var first = tableTask("first", "migration-first", firstTable);
		final var second = tableTask("second", "migration-second", secondTable);

		final var failure = assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobExecutor.order(List.of(first, second)));

		assertEquals("Migration job contains cyclic or cycle-dependent tasks: [first, second]",
				failure.getMessage());
	}

	@Test
	void allowsSelfReferencingTable() {
		final Table table = table("TREE");
		table.getConstraints().addForeignKeyConstraint("FK_TREE_PARENT",
				table.getColumns().get("ID"), table.getColumns().get("ID"));
		final var task = tableTask("tree", "migration-tree", table);

		assertEquals(List.of(task), BulkMigrationJobExecutor.order(List.of(task)));
	}

	private static BulkMigrationJobTask tableTask(final String taskId,
			final String migrationId, final Table table) {
		return tableTask(taskId, migrationId, table, options(migrationId));
	}

	private static BulkMigrationJobTask tableTask(final String taskId,
			final String migrationId, final Table table,
			final ChunkedBulkMigrationOption options) {
		return BulkMigrationJobTask.builder().taskId(taskId).sourceTable(table)
				.options(options).build();
	}

	private static BulkMigrationJobTask keysetTask(final String taskId,
			final String migrationId, final Table table) {
		return BulkMigrationJobTask.builder().taskId(taskId).keysetSource(keyset(table))
				.options(options(migrationId)).build();
	}

	private static ChunkedBulkMigrationOption options(final String migrationId) {
		return ChunkedBulkMigrationOption.builder().migrationId(migrationId).build();
	}

	private static BulkMigrationKeysetSource keyset(final Table table) {
		return new BulkMigrationKeysetSource() {
			@Override
			public Table getTable() {
				return table;
			}

			@Override
			public Iterator<Row> iterator(String resumeToken) {
				return table.getRows().iterator();
			}

			@Override
			public String resumeToken(Row row) {
				return String.valueOf(row.get("ID"));
			}
		};
	}

	private static Table table(final String name) {
		final Table table = new Table(name);
		table.getColumns().add(new Column("ID"));
		return table;
	}
}
