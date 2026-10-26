/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcDriverLibrariesTest {
	@Test
	void explicitUnregisteredClassCanBeAddedFromAlreadyLoadedJars() throws Exception {
		final var jars = new JdbcDriverJarFixture(directory);
		libraries.add(List.of(jars.secondShared, jars.dependency), "");
		assertEquals(List.of("fixture.SecondDriver"), libraries.libraries().getFirst().classes());
		libraries.add(List.of(jars.secondShared, jars.dependency), " fixture.ChildDriver ");
		assertEquals(2, libraries.libraries().size());
		for (final String name : List.of("fixture.SecondDriver", "fixture.ChildDriver")) {
			try (var pool = PlanningJdbc.open(new PlanningJdbc.Spec("jdbc:gui-added:shared_classes", "SA", name),
					new char[0], true, 1, libraries);
					var connection = pool.getConnection();
					var statement = connection.createStatement();
					var rows = statement.executeQuery("VALUES(91)")) {
				assertTrue(rows.next());
				assertEquals(91, rows.getInt(1));
			}
		}
		final String initialized = Files.readString(jars.initialized);
		assertThrows(IllegalArgumentException.class,
				() -> libraries.add(List.of(jars.driver, jars.dependency), "fixture.ChildDriver"));
		final Path duplicateService = Files.copy(jars.driver, directory.resolve("duplicate-service.jar"));
		assertThrows(IllegalArgumentException.class,
				() -> libraries.add(List.of(duplicateService, jars.dependency), ""));
		assertEquals(initialized, Files.readString(jars.initialized),
				"Rejected duplicates must not initialize a driver again");
		assertEquals(2, libraries.libraries().size());
	}

	@Test
	void differentDriversReuseDependencyJarWithoutReplacingExistingDrivers() throws Exception {
		final var jars = new JdbcDriverJarFixture(directory);
		libraries.add(List.of(jars.driver, jars.dependency), "");
		final var first = libraries
				.resolve(new PlanningJdbc.Spec("jdbc:gui-added:shared", "SA", "fixture.ChildDriver"));
		final var second = libraries.add(List.of(jars.secondShared, jars.dependency), "");
		assertEquals(List.of("fixture.SecondDriver"), second.classes());
		assertEquals(2, libraries.libraries().size());
		assertSame(first,
				libraries.resolve(new PlanningJdbc.Spec("jdbc:gui-added:shared", "SA", "fixture.ChildDriver")));
		for (final String name : List.of("fixture.ChildDriver", "fixture.SecondDriver")) {
			try (var pool = PlanningJdbc.open(new PlanningJdbc.Spec("jdbc:gui-added:shared", "SA", name), new char[0],
					true, 1, libraries);
					var connection = pool.getConnection();
					var statement = connection.createStatement();
					var rows = statement.executeQuery("VALUES(73)")) {
				assertTrue(rows.next());
				assertEquals(73, rows.getInt(1));
			}
		}
		final var duplicate = assertThrows(IllegalArgumentException.class,
				() -> libraries.add(List.of(jars.secondShared, jars.dependency), ""));
		assertTrue(duplicate.getMessage().contains("already loaded"));
		assertThrows(IllegalArgumentException.class,
				() -> libraries.add(List.of(jars.explicit, jars.dependency), "fixture.ChildDriver"));
		assertEquals(2, libraries.libraries().size());
	}

	@TempDir
	Path directory;
	private final JdbcDriverLibraries libraries = new JdbcDriverLibraries();

	@org.junit.jupiter.api.AfterEach
	void closeLibraries() throws Exception {
		libraries.close();
	}

	@Test
	void loadsServiceWithDependencyAndConnectsThroughAllPoolModes() throws Exception {
		final var jars = new JdbcDriverJarFixture(directory);
		final var contextLoader = Thread.currentThread().getContextClassLoader();
		final var library = libraries.add(List.of(jars.driver, jars.dependency), "");
		assertSame(contextLoader, Thread.currentThread().getContextClassLoader());
		assertEquals(List.of("fixture.ChildDriver"), library.classes());
		assertEquals(List.of(jars.driver.toRealPath(), jars.dependency.toRealPath()), library.files());
		final var spec = new PlanningJdbc.Spec("jdbc:gui-added:driver_test", "SA", "");
		for (final boolean readOnly : List.of(true, false)) {
			try (var pool = PlanningJdbc.open(spec, new char[0], readOnly, readOnly ? 1 : 2, libraries);
					var connection = pool.getConnection();
					var statement = connection.createStatement();
					var rows = statement.executeQuery("VALUES(42)")) {
				assertEquals(readOnly, pool.isReadOnly());
				assertTrue(rows.next());
				assertEquals(42, rows.getInt(1));
				assertEquals(readOnly, connection.isReadOnly());
			}
		}
		assertEquals("fixture.ChildDriver", libraries
				.resolve(new PlanningJdbc.Spec(spec.jdbcUrl(), "SA", "fixture.ChildDriver")).getClass().getName());
		assertNull(libraries.resolve(new PlanningJdbc.Spec("jdbc:hsqldb:mem:bundled", "SA", "")));
	}

	@Test
	void explicitClassSupportsLegacyJarAndRejectsParentClasses() throws Exception {
		final var jars = new JdbcDriverJarFixture(directory);
		assertThrows(IllegalArgumentException.class, () -> libraries.add(List.of(jars.explicit), ""));
		assertThrows(IllegalArgumentException.class,
				() -> libraries.add(List.of(jars.explicit), "org.hsqldb.jdbc.JDBCDriver"));
		assertEquals(0, libraries.libraries().size());
		libraries.add(List.of(jars.explicit), "fixture.ChildDriver");
		assertThrows(IllegalArgumentException.class,
				() -> libraries.resolve(new PlanningJdbc.Spec("jdbc:wrong:test", "", "fixture.ChildDriver")));
	}

	@Test
	void failuresAndDuplicatesDoNotReplaceLoadedLibraries() throws Exception {
		final var jars = new JdbcDriverJarFixture(directory);
		assertThrows(IllegalArgumentException.class, () -> libraries.add(List.of(jars.driver), ""));
		final var duplicatePath = assertThrows(IllegalArgumentException.class,
				() -> libraries.add(List.of(jars.driver, jars.driver.getParent().resolve("./driver.jar")), ""));
		assertTrue(duplicatePath.getMessage().contains("selected twice"));
		assertEquals(0, libraries.libraries().size());
		libraries.add(List.of(jars.driver, jars.dependency), "");
		assertThrows(IllegalArgumentException.class, () -> libraries.add(List.of(jars.driver), ""));
		assertThrows(IllegalArgumentException.class,
				() -> libraries.add(List.of(jars.explicit), "fixture.ChildDriver"));
		final Path invalid = Files.writeString(directory.resolve("broken.jar"), "not a jar");
		assertThrows(Exception.class, () -> libraries.add(List.of(invalid), ""));
		assertEquals(1, libraries.libraries().size());
		assertThrows(IllegalArgumentException.class, () -> libraries.add(List.of(), ""));
	}

	@Test
	void ambiguousUrlRequiresExplicitDriverClass() throws Exception {
		final var jars = new JdbcDriverJarFixture(directory);
		libraries.add(List.of(jars.driver, jars.dependency), "");
		libraries.add(List.of(jars.second), "");
		final var failure = assertThrows(IllegalArgumentException.class,
				() -> libraries.resolve(new PlanningJdbc.Spec("jdbc:gui-added:ambiguous", "SA", "")));
		assertTrue(failure.getMessage().contains("Specify the driver class"));
		assertEquals("fixture.SecondDriver",
				libraries.resolve(new PlanningJdbc.Spec("jdbc:gui-added:ambiguous", "SA", "fixture.SecondDriver"))
						.getClass().getName());
	}
}
