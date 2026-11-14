/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

/**
 * Every task registered by {@code com.sqlapp.db} appears in {@code gradle tasks}
 * under an sqlapp group with a description.
 */
class DbPluginTaskGroupTest {

	private static List<Task> sqlappTasks() {
		final Project project = ProjectBuilder.builder().build();
		project.getPluginManager().apply(DbPlugin.class);
		return project.getTasks().stream().filter(t -> t.getClass().getName().startsWith("com.sqlapp."))
				.collect(Collectors.toList());
	}

	@Test
	void everyTaskHasGroupAndDescription() {
		final List<Task> tasks = sqlappTasks();
		assertEquals(48, tasks.size());
		for (final Task task : tasks) {
			assertTrue(task.getGroup() != null && task.getGroup().startsWith("sqlapp "), task.getName());
			assertTrue(task.getDescription() != null && !task.getDescription().isBlank(), task.getName());
		}
	}

	@Test
	void tasksAreGroupedByPurpose() {
		final Map<String, List<String>> byGroup = new TreeMap<>(sqlappTasks().stream()
				.collect(Collectors.groupingBy(Task::getGroup, Collectors.mapping(Task::getName, Collectors.toList()))));
		assertEquals(8, byGroup.size(), byGroup.toString());
		assertTrue(byGroup.get(DbPlugin.GROUP_MIGRATION).contains("migration"));
		assertTrue(byGroup.get(DbPlugin.GROUP_SCHEMA).contains("exportSchemaXml"));
		assertTrue(byGroup.get(DbPlugin.GROUP_BULK_MIGRATION).contains("executeBulkMigrationJob"));
		assertTrue(byGroup.get(DbPlugin.GROUP_TRANSFER).contains("transferDatabase"));
		assertFalse(byGroup.get(DbPlugin.GROUP_SCHEMA).contains("migration"));
	}
}
