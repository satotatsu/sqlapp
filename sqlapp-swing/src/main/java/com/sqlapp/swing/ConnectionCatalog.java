/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Named connections shared by every connection form: each subfolder of the
 * connection folder that holds a {@code dataSource.*} settings file is one
 * connection, named after the subfolder. This is the layout Gradle builds use,
 * for example {@code src/main/config/local/dataSource.properties} referenced
 * with {@code dataSource { properties ... }}. The chosen folder is remembered
 * per user.
 */
final class ConnectionCatalog {
	/** One named connection and its settings file. */
	record Entry(String name, Path file) {
	}

	/** Remembers the connection folder between runs. */
	interface FolderStore {
		Path load();

		void save(Path folder);
	}

	static final String FILE_NAME = "dataSource";
	/** Same rule as a transfer job's runtime connection names, so a connection name can be used in jobs. */
	private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,127}");

	/** Runtime connection names written into a new transfer job. */
	record JobNames(String source, String target) {
		static final JobNames DEFAULT = new JobNames("source", "target");
	}

	/**
	 * Names for a new job: the saved connection names of the source and target
	 * forms when they are usable, otherwise {@code source} / {@code target}. Equal
	 * names bind both sides to one connection, so they are only kept when both forms
	 * chose the same saved connection.
	 */
	static JobNames jobNames(final String sourceConnection, final String targetConnection) {
		final boolean sourceNamed = sourceConnection != null && NAME.matcher(sourceConnection).matches();
		final boolean targetNamed = targetConnection != null && NAME.matcher(targetConnection).matches();
		final String source = sourceNamed ? sourceConnection : JobNames.DEFAULT.source();
		final String target = targetNamed ? targetConnection : JobNames.DEFAULT.target();
		if (source.equals(target) && !(sourceNamed && targetNamed))
			return JobNames.DEFAULT;
		return new JobNames(source, target);
	}
	static final ConnectionCatalog SESSION = new ConnectionCatalog(new PreferencesStore());

	private final FolderStore store;
	private final List<java.lang.ref.WeakReference<Runnable>> listeners = new CopyOnWriteArrayList<>();
	private Path folder;

	ConnectionCatalog(final FolderStore store) {
		this.store = Objects.requireNonNull(store);
		Path remembered = null;
		try {
			remembered = store.load();
		} catch (final RuntimeException unavailable) {
			// Preferences may be unavailable (locked-down desktops); start without a
			// folder.
		}
		folder = remembered != null && Files.isDirectory(remembered) ? remembered : null;
	}

	Path folder() {
		return folder;
	}

	void setFolder(final Path value) {
		if (value != null && !Files.isDirectory(value))
			throw new IllegalArgumentException("Choose an existing connection folder: " + value);
		folder = value == null ? null : value.toAbsolutePath().normalize();
		try {
			store.save(folder);
		} catch (final RuntimeException unavailable) {
			// Keep the folder for this session even when it cannot be remembered.
		}
		changed();
	}

	/** Connections in the folder, sorted by name; empty without a folder. */
	List<Entry> entries() {
		final var entries = new ArrayList<Entry>();
		if (folder == null || !Files.isDirectory(folder))
			return entries;
		try (Stream<Path> children = Files.list(folder)) {
			children.filter(Files::isDirectory).sorted().forEach(child -> {
				final Path file = settingsFile(child);
				if (file != null)
					entries.add(new Entry(child.getFileName().toString(), file));
			});
		} catch (final IOException unreadable) {
			// An unreadable folder lists no connections; the form shows the folder in its
			// tooltip.
		}
		return entries;
	}

	/** The settings file of a connection subfolder, preferring YAML, or null. */
	static Path settingsFile(final Path connectionFolder) {
		for (final String extension : com.sqlapp.data.db.command.datasource.DataSourceConfigFiles.EXTENSIONS) {
			final Path file = connectionFolder.resolve(FILE_NAME + "." + extension);
			if (Files.isRegularFile(file))
				return file;
		}
		return null;
	}

	/**
	 * Where a new connection with this name is saved; refuses names that are not
	 * plain folder names.
	 */
	Path newConnectionFile(final String name) {
		if (folder == null)
			throw new IllegalArgumentException("Choose a connection folder first.");
		final String trimmed = name == null ? "" : name.trim();
		if (!NAME.matcher(trimmed).matches())
			throw new IllegalArgumentException(
					"Start the connection name with a letter, then use letters, digits, '.', '_' or '-', for example local or sqlserver.");
		return folder.resolve(trimmed).resolve(FILE_NAME + ".yaml");
	}

	/**
	 * Registers a listener held weakly: the caller keeps it alive for as long as it
	 * needs updates.
	 */
	void addListener(final Runnable listener) {
		listeners.add(new java.lang.ref.WeakReference<>(Objects.requireNonNull(listener)));
	}

	/** Tells forms that the folder or its connections changed. */
	void changed() {
		for (final var reference : listeners) {
			final Runnable listener = reference.get();
			if (listener == null)
				listeners.remove(reference);
			else
				listener.run();
		}
	}

	private static final class PreferencesStore implements FolderStore {
		private static final String KEY = "connectionFolder";

		@Override
		public Path load() {
			final String value = java.util.prefs.Preferences.userNodeForPackage(ConnectionCatalog.class).get(KEY, null);
			return value == null ? null : Path.of(value);
		}

		@Override
		public void save(final Path folder) {
			final var preferences = java.util.prefs.Preferences.userNodeForPackage(ConnectionCatalog.class);
			if (folder == null)
				preferences.remove(KEY);
			else
				preferences.put(KEY, folder.toString());
		}
	}
}
