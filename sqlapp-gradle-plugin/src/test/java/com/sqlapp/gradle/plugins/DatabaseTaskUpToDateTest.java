/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.ExportSchemaXmlCommand;
import com.sqlapp.gradle.plugins.properties.OptionalDataSourceTaskProperty;

/**
 * Tasks that read or change a live database must never be UP-TO-DATE, because
 * the database state is not a tracked input.
 */
class DatabaseTaskUpToDateTest {

	@Test
	void databaseTasksAreNeverUpToDate() {
		final Project project = ProjectBuilder.builder().build();
		project.getPluginManager().apply(DbPlugin.class);
		for (final String name : new String[] { "exportSchemaXml", "validateBulkMigrationTarget",
				"executeBulkMigrationJob", "executeBulkMigrationJobRepair", "migration", "migrationPlan",
				"migrationValidate" }) {
			assertFalse(isUpToDateAllowed(project.getTasks().getByName(name)), name);
		}
		// Not registered by the plugin; users register it themselves.
		assertFalse(isUpToDateAllowed(project.getTasks().register("myExportData", ExportDataTask.class).get()));
	}

	@Test
	void optionalDataSourceTaskMayBeUpToDateOnlyWithoutDataSource() {
		final Project project = ProjectBuilder.builder().build();
		final OptionalDbTask task = project.getTasks().register("optionalDbTask", OptionalDbTask.class).get();
		assertTrue(isUpToDateAllowed(task));
		task.getDataSource().getJdbcUrl().set("jdbc:hsqldb:mem:optional_db_task");
		assertFalse(isUpToDateAllowed(task));
	}

	private static boolean isUpToDateAllowed(final Task task) {
		return ((TaskOutputsInternal) task.getOutputs()).getUpToDateSpec().isSatisfiedBy((TaskInternal) task);
	}

	public abstract static class OptionalDbTask extends AbstractDbTask<ExportSchemaXmlCommand>
			implements OptionalDataSourceTaskProperty {
		@Override
		protected ExportSchemaXmlCommand createCommand() {
			return new ExportSchemaXmlCommand();
		}
	}
}
