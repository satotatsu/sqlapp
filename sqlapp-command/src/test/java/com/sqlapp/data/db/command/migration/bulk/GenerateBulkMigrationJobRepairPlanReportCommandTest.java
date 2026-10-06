/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairPlanner;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairTask;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairOption;
import com.sqlapp.jdbc.bulk.BulkMigrationVerifier;
import com.sqlapp.util.MessageDigests;

class GenerateBulkMigrationJobRepairPlanReportCommandTest {
	@TempDir
	Path directory;

	@Test
	void writesAnEmptyReviewedPlanWithoutExecutingDatabaseWork() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:repair_plan_report")) {
			final var plan = BulkMigrationJobRepairPlanner.plan(connection, List.of());
			final Path target = directory.resolve("repair-plan.json");
			final var command = new GenerateBulkMigrationJobRepairPlanReportCommand();
			command.setPlan(plan);
			command.setTargetFile(target.toFile());

			command.run();

			final var report = new BulkMigrationJobRepairPlanReportIO().read(target);
			assertEquals(plan.getFingerprint(), report.planFingerprint());
			assertEquals(List.of(), report.tasks());
			assertEquals(report, command.getReport());
			assertEquals("sha256:" + MessageDigests.SHA256.checksumAsString(target.toFile()),
					command.getReportFingerprint());
		}
	}

	@Test
	void rejectsAnOversizedGeneratedReportAndInvalidLimit() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:repair_plan_size")) {
			final var command = new GenerateBulkMigrationJobRepairPlanReportCommand();
			command.setPlan(BulkMigrationJobRepairPlanner.plan(connection, List.of()));
			command.setTargetFile(directory.resolve("bounded-plan.json").toFile());
			command.setMaxRepairPlanReportFileSizeBytes(0L);
			assertThrows(CommandException.class, command::run);
			command.setMaxRepairPlanReportFileSizeBytes(1L);
			assertThrows(CommandException.class, command::run);
		}
	}

	@Test
	void requiresThePlanAndTargetFile() throws Exception {
		final var missingPlan = new GenerateBulkMigrationJobRepairPlanReportCommand();
		missingPlan.setTargetFile(directory.resolve("missing-plan.json").toFile());
		assertThrows(CommandException.class, missingPlan::run);

		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:repair_plan_missing_file")) {
			final var missingFile = new GenerateBulkMigrationJobRepairPlanReportCommand();
			missingFile.setPlan(BulkMigrationJobRepairPlanner.plan(connection, List.of()));
			assertThrows(CommandException.class, missingFile::run);
		}
	}

	@Test
	void writesDependencyOrderedTasksAndMismatchDetails() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:repair_plan_tasks")) {
			final Table parent = table("PARENT", false);
			final Table child = table("CHILD", true);
			child.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT",
					new Column[] { child.getColumns().get("PARENT_ID") },
					new Column[] { parent.getColumns().get("ID") });
			final Table actualParent = table("PARENT", false);
			actualParent.getRows().add(row -> row.put("ID", 1));
			final Table actualChild = table("CHILD", true);
			actualChild.getRows().add(row -> {
				row.put("ID", 10);
				row.put("PARENT_ID", 1);
			});
			final var option = BulkMigrationRepairOption.defaults();
			final var childTask = BulkMigrationJobRepairTask.builder().taskId("child").expected(child)
					.verificationResult(BulkMigrationVerifier.verify(child, actualChild, 1)).options(option).build();
			final var parentTask = BulkMigrationJobRepairTask.builder().taskId("parent").expected(parent)
					.verificationResult(BulkMigrationVerifier.verify(parent, actualParent, 1)).options(option).build();
			final var plan = BulkMigrationJobRepairPlanner.plan(connection, List.of(childTask, parentTask));
			final Path target = directory.resolve("repair-plan-tasks.json");
			final var command = new GenerateBulkMigrationJobRepairPlanReportCommand();
			command.setPlan(plan);
			command.setTargetFile(target.toFile());

			command.run();

			final var report = new BulkMigrationJobRepairPlanReportIO().read(target, plan.getFingerprint());
			assertEquals(List.of("parent", "child"),
					report.tasks().stream().map(BulkMigrationJobRepairPlanReport.Task::taskId).toList());
			assertEquals(2, report.mismatchChunks());
			assertEquals(0, report.estimatedReplayRows());
			assertEquals(0, report.tasks().get(0).repairPlan().mismatchChunks().get(0).expectedRows());
		}
	}

	private static Table table(final String name, final boolean child) {
		final Table table = new Table(name);
		table.getColumns().add(new Column("ID"));
		if (child) {
			table.getColumns().add(new Column("PARENT_ID"));
		}
		table.setPrimaryKey("PK_" + name, table.getColumns().get("ID"));
		return table;
	}
}
