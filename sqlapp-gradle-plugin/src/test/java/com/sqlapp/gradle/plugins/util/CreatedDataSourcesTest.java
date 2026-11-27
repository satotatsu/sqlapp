/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import com.sqlapp.gradle.plugins.extension.DataSourceExtension;
import com.zaxxer.hikari.HikariDataSource;

class CreatedDataSourcesTest {

	private static DataSource createDataSource(final String name) {
		final Project project = ProjectBuilder.builder().build();
		final DataSourceExtension extension = project.getObjects().newInstance(DataSourceExtension.class);
		extension.getJdbcUrl().set("jdbc:hsqldb:mem:" + name);
		extension.getUsername().set("SA");
		return extension.createDataSource();
	}

	@Test
	void closesDataSourcesCreatedInsideScope() {
		final HikariDataSource ds;
		try (CreatedDataSources.Scope scope = CreatedDataSources.open()) {
			ds = assertInstanceOf(HikariDataSource.class, createDataSource("created_in_scope"));
			assertFalse(ds.isClosed());
		}
		assertTrue(ds.isClosed());
	}

	@Test
	void leavesDataSourcesCreatedOutsideScopeToCaller() {
		final HikariDataSource ds = assertInstanceOf(HikariDataSource.class,
				createDataSource("created_outside_scope"));
		try {
			try (CreatedDataSources.Scope scope = CreatedDataSources.open()) {
				// nothing registered
			}
			assertFalse(ds.isClosed());
		} finally {
			ds.close();
		}
	}

	@Test
	void nestedScopeClosesOnlyItsOwnDataSources() {
		final HikariDataSource outer;
		final HikariDataSource inner;
		try (CreatedDataSources.Scope outerScope = CreatedDataSources.open()) {
			outer = (HikariDataSource) createDataSource("outer_scope");
			try (CreatedDataSources.Scope innerScope = CreatedDataSources.open()) {
				inner = (HikariDataSource) createDataSource("inner_scope");
			}
			assertTrue(inner.isClosed());
			assertFalse(outer.isClosed());
		}
		assertTrue(outer.isClosed());
	}
}
