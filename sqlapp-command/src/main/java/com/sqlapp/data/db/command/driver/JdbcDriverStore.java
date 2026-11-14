/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.driver;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.util.YamlConverter;

import tools.jackson.core.type.TypeReference;

/**
 * Downloaded JDBC drivers kept in a user folder, by default
 * {@code ~/.sqlapp/drivers}:
 * <ul>
 * <li>{@code repository/} - verified JARs in Maven layout</li>
 * <li>{@code drivers.yaml} - installed drivers, loaded at the next start</li>
 * <li>{@code settings.yaml} - Maven repository URLs, Maven Central by
 * default</li>
 * </ul>
 */
public final class JdbcDriverStore {
	static final String DRIVERS = "drivers.yaml";
	static final String SETTINGS = "settings.yaml";
	private final Path home;

	/**
	 * An installed driver.
	 *
	 * @param coordinate  the downloaded artifact
	 * @param driverClass JDBC driver class, or null for service discovery
	 * @param files       its JAR and dependency JARs
	 */
	public record Installed(MavenCoordinate coordinate, String driverClass, List<Path> files) {
		public Installed {
			Objects.requireNonNull(coordinate, "coordinate");
			driverClass = driverClass == null || driverClass.isBlank() ? null : driverClass.trim();
			files = List.copyOf(files);
		}
	}

	public JdbcDriverStore(final Path home) {
		this.home = Objects.requireNonNull(home, "home").toAbsolutePath().normalize();
	}

	/** {@code ~/.sqlapp/drivers}. */
	public static Path defaultHome() {
		return Path.of(System.getProperty("user.home"), ".sqlapp", "drivers");
	}

	public Path home() {
		return home;
	}

	/** Repository URLs to download from; Maven Central unless changed. */
	public synchronized List<URI> repositories() {
		final Map<String, Object> settings = read(home.resolve(SETTINGS));
		final Object value = settings.get("repositories");
		if (value == null)
			return List.of(MavenRepositoryClient.MAVEN_CENTRAL);
		if (!(value instanceof List<?> list))
			throw new IllegalArgumentException("'repositories' must be a list in " + home.resolve(SETTINGS));
		final var result = new ArrayList<URI>();
		for (final Object item : list) {
			try {
				result.add(MavenRepositoryClient.normalize(URI.create(String.valueOf(item).trim())));
			} catch (final IllegalArgumentException invalid) {
				throw new IllegalArgumentException(
						"Invalid repository in " + home.resolve(SETTINGS) + ": " + invalid.getMessage());
			}
		}
		if (result.isEmpty())
			throw new IllegalArgumentException("No repositories in " + home.resolve(SETTINGS));
		return List.copyOf(result);
	}

	public synchronized void setRepositories(final List<URI> repositories) throws IOException {
		final var normalized = new MavenRepositoryClient(repositories).repositories();
		final var settings = new LinkedHashMap<String, Object>(read(home.resolve(SETTINGS)));
		settings.put("repositories", normalized.stream().map(URI::toString).toList());
		write(home.resolve(SETTINGS), "# sqlapp JDBC driver download settings.\n", settings);
	}

	public MavenRepositoryClient client() {
		return new MavenRepositoryClient(repositories());
	}

	/** Installed drivers, in installation order. */
	public synchronized List<Installed> installed() {
		final Path file = home.resolve(DRIVERS);
		final Object value = read(file).get("drivers");
		if (value == null)
			return List.of();
		if (!(value instanceof List<?> list))
			throw new IllegalArgumentException("'drivers' must be a list in " + file);
		final var result = new ArrayList<Installed>();
		for (final Object item : list) {
			if (!(item instanceof Map<?, ?> entry))
				throw new IllegalArgumentException("Each driver must be a mapping in " + file);
			try {
				final var coordinate = MavenCoordinate.parse(String.valueOf(entry.get("coordinate")));
				if (coordinate.version() == null)
					throw new IllegalArgumentException("Version is missing: " + coordinate);
				final var files = new ArrayList<Path>();
				if (entry.get("files") instanceof List<?> paths)
					for (final Object path : paths)
						files.add(inside(String.valueOf(path)));
				if (files.isEmpty())
					throw new IllegalArgumentException("No files for " + coordinate);
				final Object driverClass = entry.get("driverClass");
				result.add(new Installed(coordinate, driverClass == null ? null : String.valueOf(driverClass), files));
			} catch (final IllegalArgumentException invalid) {
				throw new IllegalArgumentException("Invalid driver in " + file + ": " + invalid.getMessage());
			}
		}
		return List.copyOf(result);
	}

	private Path inside(final String relative) {
		final Path path = home.resolve(relative).normalize();
		if (!path.startsWith(home.resolve("repository")))
			throw new IllegalArgumentException("File outside the driver repository: " + relative);
		return path;
	}

	/**
	 * Downloads a driver and its runtime dependencies, verifies each file and
	 * records the driver for the next start. A driver of the same module replaces
	 * the earlier entry; its files stay until {@link #remove} or reinstalling frees
	 * them.
	 *
	 * @param progress receives one line per step; may be null
	 */
	public Installed install(final MavenCoordinate coordinate, final String driverClass,
			final MavenRepositoryClient client, final Consumer<String> progress) throws IOException {
		final Consumer<String> log = progress == null ? line -> {
		} : progress;
		final var artifacts = new MavenDependencyResolver(client).resolve(coordinate, log);
		if (artifacts.isEmpty())
			throw new IOException("No JAR files to download for " + coordinate);
		final var files = new ArrayList<Path>();
		for (final MavenCoordinate artifact : artifacts) {
			if (Thread.currentThread().isInterrupted())
				throw new java.io.InterruptedIOException("Download cancelled.");
			final Path target = inside("repository/" + artifact.path());
			if (!Files.isRegularFile(target)) {
				log.accept("Downloading " + artifact);
				final var download = client.download(artifact.path(), MavenRepositoryClient.MAX_ARTIFACT_BYTES);
				if (download == null)
					throw new IOException("Not found in the Maven repositories: " + artifact);
				Files.createDirectories(target.getParent());
				AtomicMigrationFile.write(target, temporary -> Files.write(temporary, download.content()));
			} else
				log.accept("Already downloaded " + artifact);
			files.add(target);
		}
		final var installed = new Installed(coordinate, driverClass, files);
		synchronized (this) {
			final var drivers = new ArrayList<Installed>();
			for (final Installed existing : installed())
				if (!same(existing.coordinate(), coordinate))
					drivers.add(existing);
			drivers.add(installed);
			save(drivers);
		}
		log.accept("Installed " + coordinate + " (" + files.size() + " JAR" + (files.size() == 1 ? "" : "s") + ")");
		return installed;
	}

	private static boolean same(final MavenCoordinate left, final MavenCoordinate right) {
		return left.module().equals(right.module()) && Objects.equals(left.classifier(), right.classifier());
	}

	/**
	 * Forgets an installed driver and deletes the JARs no other installed driver
	 * uses.
	 *
	 * @return files that could not be deleted now, for example because they are
	 *         loaded; they are not used at the next start
	 */
	public synchronized List<Path> remove(final MavenCoordinate coordinate) throws IOException {
		final var drivers = new ArrayList<Installed>();
		Installed removed = null;
		for (final Installed existing : installed())
			if (existing.coordinate().equals(coordinate))
				removed = existing;
			else
				drivers.add(existing);
		if (removed == null)
			throw new IllegalArgumentException("Driver is not installed: " + coordinate);
		save(drivers);
		final var kept = new ArrayList<Path>();
		for (final Path file : removed.files()) {
			if (drivers.stream().anyMatch(other -> other.files().contains(file)))
				continue;
			try {
				Files.deleteIfExists(file);
			} catch (final IOException locked) {
				kept.add(file);
			}
		}
		return List.copyOf(kept);
	}

	private void save(final List<Installed> drivers) throws IOException {
		final var list = new ArrayList<Map<String, Object>>();
		for (final Installed driver : drivers) {
			final var entry = new LinkedHashMap<String, Object>();
			entry.put("coordinate", driver.coordinate().toString());
			if (driver.driverClass() != null)
				entry.put("driverClass", driver.driverClass());
			entry.put("files",
					driver.files().stream().map(file -> home.relativize(file).toString().replace('\\', '/')).toList());
			list.add(entry);
		}
		final var root = new LinkedHashMap<String, Object>();
		root.put("drivers", list);
		write(home.resolve(DRIVERS), "# JDBC drivers downloaded by sqlapp; loaded at start.\n", root);
	}

	private static Map<String, Object> read(final Path file) {
		if (!Files.exists(file))
			return Map.of();
		try {
			final Map<String, Object> tree = new YamlConverter().fromJsonString(file.toFile(),
					new TypeReference<Map<String, Object>>() {
					});
			return tree == null ? Map.of() : tree;
		} catch (final RuntimeException failure) {
			throw new IllegalArgumentException(
					"Unable to read " + file + " (" + failure.getClass().getSimpleName() + ")");
		}
	}

	private static void write(final Path file, final String comment, final Map<String, Object> content)
			throws IOException {
		Files.createDirectories(file.getParent());
		final String text = comment
				+ new YamlConverter().toJsonString(new LinkedHashMap<>(content)).replaceFirst("^---\\s*\\n", "");
		AtomicMigrationFile.write(file, temporary -> Files.writeString(temporary, text, StandardCharsets.UTF_8));
	}
}
