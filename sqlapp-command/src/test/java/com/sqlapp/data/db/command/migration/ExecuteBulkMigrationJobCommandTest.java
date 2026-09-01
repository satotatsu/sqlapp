/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.test.AbstractDbCommandTest;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlanner;

class ExecuteBulkMigrationJobCommandTest extends AbstractDbCommandTest {
	@Test
	void executesProgrammaticPlanAgainstConfiguredDataSource() {
		try (var dataSource = newDataSource()) {
			final var command = new ExecuteBulkMigrationJobCommand();
			command.setDataSource(dataSource);
			command.setCloseDataSource(false);
			command.setPlan(BulkMigrationJobPlanner.plan(List.of()));

			command.run();

			assertNotNull(command.getResult());
			assertEquals(command.getPlan().getFingerprint(),
					command.getResult().getPlanFingerprint());
			assertEquals(List.of(), command.getResult().getTasks());
		}
	}

	@Test
	void executesWithDedicatedDatabaseLeaseConnection() {
		try (var dataSource = newDataSource()) {
			final var command = new ExecuteBulkMigrationJobCommand();
			command.setDataSource(dataSource);
			command.setCloseDataSource(false);
			command.setPlan(BulkMigrationJobPlanner.plan(List.of()));
			command.setLeaseConfiguration(
					BulkMigrationJobLeaseConfiguration.database("gradle-worker"));

			command.run();

			assertNotNull(command.getResult());
			assertEquals(command.getPlan().getFingerprint(),
					command.getResult().getPlanFingerprint());
		}
	}
}
