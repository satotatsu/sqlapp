/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairPlanner;

class GenerateBulkMigrationJobRepairPlanReportCommandTest {
	@TempDir
	Path directory;

	@Test
	void writesAnEmptyReviewedPlanWithoutExecutingDatabaseWork() throws Exception {
		try (var connection = DriverManager.getConnection(
				"jdbc:hsqldb:mem:repair_plan_report")) {
			final var plan = BulkMigrationJobRepairPlanner.plan(connection, List.of());
			final Path target = directory.resolve("repair-plan.json");
			final var command = new GenerateBulkMigrationJobRepairPlanReportCommand();
			command.setPlan(plan);
			command.setTargetFile(target.toFile());

			command.run();

			final var report = new BulkMigrationJobRepairPlanReportIO().read(target);
			assertEquals(plan.getFingerprint(), report.planFingerprint());
			assertEquals(List.of(), report.tasks());
		}
	}

	@Test
	void requiresThePlanAndTargetFile() throws Exception {
		final var missingPlan = new GenerateBulkMigrationJobRepairPlanReportCommand();
		missingPlan.setTargetFile(directory.resolve("missing-plan.json").toFile());
		assertThrows(CommandException.class, missingPlan::run);

		try (var connection = DriverManager.getConnection(
				"jdbc:hsqldb:mem:repair_plan_missing_file")) {
			final var missingFile = new GenerateBulkMigrationJobRepairPlanReportCommand();
			missingFile.setPlan(BulkMigrationJobRepairPlanner.plan(connection, List.of()));
			assertThrows(CommandException.class, missingFile::run);
		}
	}
}
