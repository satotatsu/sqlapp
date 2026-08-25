/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.rowiterator.JdbcDynamicRowIteratorHandler;

/** Loads SQLite files through the SQLite MetadataReader. */
public final class SqliteFileLoader {
	private SqliteFileLoader() {
	}

	public static Schema load(final Path file) throws IOException {
		return loadSchema(file);
	}

	public static Schema loadSchema(final Path file) throws IOException {
		return loadSchema(file, "main", Map.of());
	}

	/**
	 * Loads a selected SQLite database after attaching the supplied files.
	 *
	 * @param file primary database file
	 * @param databaseName {@code main}, or a key in {@code attachedDatabases}
	 * @param attachedDatabases database name to file mapping
	 */
	public static Schema loadSchema(final Path file, final String databaseName,
			final Map<String, Path> attachedDatabases) throws IOException {
		final Path normalized = normalize(file);
		final String selectedDatabase = validateDatabaseName(databaseName);
		final Map<String, Path> attachments = normalizeAttachments(
				attachedDatabases);
		if (!"main".equalsIgnoreCase(selectedDatabase)
				&& !attachments.keySet().stream().anyMatch(
						name -> name.equalsIgnoreCase(selectedDatabase))) {
			throw new IllegalArgumentException(
					"Attached SQLite database not found: " + selectedDatabase);
		}
		final SQLiteDataSource dataSource = createDataSource(normalized,
				attachments);
		try (var connection = dataSource.getConnection()) {
			final Schema schema = SchemaUtils
					.getSchema(connection, selectedDatabase)
					.orElseThrow(() -> new IOException(
							"SQLite database not found: " + selectedDatabase));
			for (final Table table : schema.getTables()) {
				table.setRowIteratorHandler(
						new JdbcDynamicRowIteratorHandler(dataSource));
			}
			return schema;
		} catch (SQLException e) {
			throw readException(normalized, e);
		} catch (RuntimeException e) {
			throw readException(normalized, e);
		}
	}

	public static Table loadTable(final Path file, final String tableName)
			throws IOException {
		final Table table = loadSchema(file).getTables().get(tableName);
		if (table == null) {
			throw new IllegalArgumentException(
					"SQLite table not found: " + tableName);
		}
		return table;
	}

	/** Loads one table from a selected attached SQLite database. */
	public static Table loadTable(final Path file, final String databaseName,
			final Map<String, Path> attachedDatabases, final String tableName)
			throws IOException {
		final Table table = loadSchema(file, databaseName, attachedDatabases)
				.getTables().get(tableName);
		if (table == null) {
			throw new IllegalArgumentException(
					"SQLite table not found: " + tableName);
		}
		return table;
	}

	private static Path normalize(final Path file) throws IOException {
		final Path normalized = file.toAbsolutePath().normalize();
		if (!Files.isRegularFile(normalized)) {
			throw new IOException("SQLite file not found: " + normalized);
		}
		return normalized;
	}

	private static SQLiteDataSource createDataSource(final Path file,
			final Map<String, Path> attachments) {
		final SQLiteConfig config = new SQLiteConfig();
		config.setReadOnly(true);
		config.enforceForeignKeys(true);
		final SQLiteDataSource dataSource = attachments.isEmpty()
				? new SQLiteDataSource(config)
				: new AttachedSQLiteDataSource(config, attachments);
		dataSource.setUrl("jdbc:sqlite:" + file);
		return dataSource;
	}

	private static Map<String, Path> normalizeAttachments(
			final Map<String, Path> attachedDatabases) throws IOException {
		final Map<String, Path> result = new LinkedHashMap<>();
		for (final var entry : Objects.requireNonNull(attachedDatabases,
				"attachedDatabases").entrySet()) {
			final String name = validateDatabaseName(entry.getKey());
			if ("main".equalsIgnoreCase(name) || "temp".equalsIgnoreCase(name)) {
				throw new IllegalArgumentException(
						"Reserved SQLite database name: " + name);
			}
			result.put(name, normalize(entry.getValue()));
		}
		return Map.copyOf(result);
	}

	private static String validateDatabaseName(final String value) {
		final String name = Objects.requireNonNull(value, "databaseName");
		if (name.isBlank() || name.indexOf('\0') >= 0) {
			throw new IllegalArgumentException(
					"Invalid SQLite database name: " + name);
		}
		return name;
	}

	private static IOException readException(final Path file,
			final Throwable cause) {
		Throwable current = cause;
		boolean notDatabase = false;
		while (current != null) {
			if (current.getMessage() != null && current.getMessage()
					.toLowerCase(java.util.Locale.ROOT)
					.contains("not a database")) {
				notDatabase = true;
				break;
			}
			current = current.getCause();
		}
		final String detail = notDatabase
				? " (the file may be encrypted; encrypted SQLite files are not supported)"
				: "";
		return new IOException("Failed to read SQLite file: " + file + detail,
				cause);
	}

	private static final class AttachedSQLiteDataSource
			extends SQLiteDataSource {
		private final Map<String, Path> attachments;

		private AttachedSQLiteDataSource(final SQLiteConfig config,
				final Map<String, Path> attachments) {
			super(config);
			this.attachments = attachments;
		}

		@Override
		public org.sqlite.SQLiteConnection getConnection(final String user,
				final String password) throws SQLException {
			return attach(super.getConnection(user, password));
		}

		private org.sqlite.SQLiteConnection attach(
				final org.sqlite.SQLiteConnection connection)
				throws SQLException {
			try (var statement = connection.createStatement()) {
				for (final var entry : attachments.entrySet()) {
					final String path = entry.getValue().toString()
							.replace("'", "''");
					final String name = entry.getKey().replace("\"", "\"\"");
					statement.execute("ATTACH DATABASE '" + path + "' AS \""
							+ name + "\"");
				}
				return connection;
			} catch (SQLException e) {
				try {
					connection.close();
				} catch (SQLException suppressed) {
					e.addSuppressed(suppressed);
				}
				throw e;
			}
		}
	}
}
