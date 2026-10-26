/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.jar.JarFile;

/**
 * Session-only driver libraries. Added drivers never replace bundled drivers.
 */
final class JdbcDriverLibraries implements AutoCloseable {
	static final JdbcDriverLibraries SESSION = new JdbcDriverLibraries();

	record Library(List<Path> files, List<String> classes, URLClassLoader loader, List<Driver> drivers) {
	}

	private volatile List<Library> libraries = List.of();

	List<Library> libraries() {
		return libraries;
	}

	@Override
	public synchronized void close() throws java.io.IOException {
		java.io.IOException problem = null;
		for (final var library : libraries) {
			try {
				library.loader().close();
			} catch (final java.io.IOException failure) {
				if (problem == null)
					problem = failure;
				else
					problem.addSuppressed(failure);
			}
		}
		libraries = List.of();
		if (problem != null)
			throw problem;
	}

	synchronized Library add(final List<Path> files, final String className) throws Exception {
		if (files.isEmpty())
			throw new IllegalArgumentException("Select at least one JDBC driver JAR.");
		final String requestedClass = className == null ? "" : className.trim();
		if (!requestedClass.isEmpty())
			requireNewDriverClass(requestedClass);
		final var paths = new ArrayList<Path>();
		for (final var file : files) {
			final Path path = file.toRealPath();
			if (!Files.isRegularFile(path) || !Files.isReadable(path)
					|| !path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar"))
				throw new IllegalArgumentException("Select a readable .jar file: " + file);
			if (paths.contains(path))
				throw new IllegalArgumentException("JAR is selected twice: " + path);
			try (var jar = new JarFile(path.toFile())) {
				jar.size();
			}
			paths.add(path);
		}
		if (requestedClass.isEmpty() && paths.stream()
				.allMatch(path -> libraries.stream().anyMatch(library -> library.files().contains(path))))
			throw new IllegalArgumentException(
					"All selected JARs are already loaded. Select a new driver JAR, or enter an unregistered driver class from these JARs.");
		final var urls = new java.net.URL[paths.size()];
		for (int index = 0; index < paths.size(); index++)
			urls[index] = paths.get(index).toUri().toURL();
		final var loader = new URLClassLoader(urls, JdbcDriverLibraries.class.getClassLoader());
		final var thread = Thread.currentThread();
		final var previousLoader = thread.getContextClassLoader();
		try {
			thread.setContextClassLoader(loader);
			final var drivers = new ArrayList<Driver>();
			if (!requestedClass.isEmpty()) {
				final var type = Class.forName(requestedClass, false, loader);
				if (type.getClassLoader() != loader || !Driver.class.isAssignableFrom(type))
					throw new IllegalArgumentException(
							"Class must be a JDBC Driver provided by the selected JARs: " + className);
				drivers.add((Driver) type.getConstructor().newInstance());
			} else {
				for (final var provider : ServiceLoader.load(Driver.class, loader).stream().toList()) {
					if (provider.type().getClassLoader() == loader) {
						requireNewDriverClass(provider.type().getName());
						drivers.add(provider.get());
					}
				}
			}
			if (drivers.isEmpty())
				throw new IllegalArgumentException(
						"No JDBC driver service found. Enter its driver class, or select the driver JAR with its dependencies.");
			final var classes = drivers.stream().map(driver -> driver.getClass().getName()).distinct().toList();
			classes.forEach(this::requireNewDriverClass);
			final var library = new Library(List.copyOf(paths), classes, loader, List.copyOf(drivers));
			final var updated = new ArrayList<>(libraries);
			updated.add(library);
			libraries = List.copyOf(updated);
			return library;
		} catch (final Exception | LinkageError | java.util.ServiceConfigurationError failure) {
			try {
				loader.close();
			} catch (final Exception close) {
				failure.addSuppressed(close);
			}
			throw new IllegalArgumentException(
					"Unable to load JDBC driver JARs. Check the driver class, dependencies and Java 21 compatibility.",
					failure);
		} finally {
			thread.setContextClassLoader(previousLoader);
		}
	}

	private void requireNewDriverClass(final String name) {
		if (libraries.stream().anyMatch(library -> library.classes().contains(name)))
			throw new IllegalArgumentException(
					"Driver class is already loaded: " + name + ". Restart to use a different version.");
	}

	Driver resolve(final PlanningJdbc.Spec spec) {
		final String name = spec.driverClass() == null ? "" : spec.driverClass().trim();
		if (name.isEmpty()) {
			try {
				DriverManager.getDriver(spec.jdbcUrl());
				return null;
			} catch (final SQLException absent) {
				/* Try the explicitly added session libraries. */ }
		}
		final var matches = new ArrayList<Driver>();
		for (final var library : libraries)
			for (final var driver : library.drivers()) {
				if (!name.isEmpty() && !driver.getClass().getName().equals(name))
					continue;
				final var thread = Thread.currentThread();
				final var previousLoader = thread.getContextClassLoader();
				try {
					thread.setContextClassLoader(driver.getClass().getClassLoader());
					if (driver.acceptsURL(spec.jdbcUrl()))
						matches.add(driver);
					else if (!name.isEmpty())
						throw new IllegalArgumentException(
								"Selected JDBC driver does not accept this JDBC URL: " + name);
				} catch (final SQLException | LinkageError failure) {
					throw new IllegalArgumentException("JDBC driver URL check failed. Check driver dependencies.",
							failure);
				} finally {
					thread.setContextClassLoader(previousLoader);
				}
			}
		if (matches.size() > 1)
			throw new IllegalArgumentException(
					"Multiple added JDBC drivers accept this URL. Specify the driver class in Advanced options.");
		return matches.isEmpty() ? null : matches.getFirst();
	}
}
