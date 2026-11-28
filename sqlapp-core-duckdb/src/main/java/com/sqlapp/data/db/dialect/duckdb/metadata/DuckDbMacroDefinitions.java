/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.duckdb.metadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.duckdb.DuckDBConnection;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.duckdb.util.DuckDbDdlParser;

/**
 * Optional complete macro extraction: copy catalog objects, never source rows.
 */
final class DuckDbMacroDefinitions {

	record Key(String schema, String name, boolean table) {
	}

	private DuckDbMacroDefinitions() {
	}

	static Map<Key, String> read(final Connection connection, final Dialect dialect, final String catalog)
			throws SQLException {
		if (!connection.getAutoCommit()) {
			throw new SQLException("Complete DuckDB macro definitions require an auto-commit connection; "
					+ "the schema snapshot cannot include uncommitted catalog changes");
		}
		final String snapshot = "sqlapp_macro_" + UUID.randomUUID().toString().replace("-", "");
		final Path directory;
		try {
			directory = Files.createTempDirectory("sqlapp-duckdb-macros-");
		} catch (IOException e) {
			throw new SQLException("Cannot create temporary directory for DuckDB macro definitions", e);
		}
		Throwable failure = null;
		try (Connection copy = connection.unwrap(DuckDBConnection.class).duplicate()) {
			boolean attached = false;
			try {
				execute(copy, "ATTACH ':memory:' AS " + dialect.quote(snapshot));
				attached = true;
				// One schema copy/export per catalog, not a SELECT or probe per macro.
				execute(copy, "COPY FROM DATABASE " + dialect.quote(catalog) + " TO " + dialect.quote(snapshot)
						+ " (SCHEMA)");
				// Export only needs macros. Removing snapshot tables avoids DuckDB's
				// self-referencing-FK export restriction. Macros retain their bodies
				// without binding those bodies to the disposable tables.
				final java.util.List<String> tables = new java.util.ArrayList<>();
				try (var query = copy.prepareStatement(
						"SELECT schema_name, table_name FROM duckdb_tables() WHERE database_name = ?")) {
					query.setString(1, snapshot);
					try (var rows = query.executeQuery()) {
						while (rows.next())
							tables.add(dialect.getObjectFullName(snapshot, rows.getString(1), rows.getString(2)));
					}
				}
				final Map<String, java.util.List<String>> children = new HashMap<>();
				try (var query = copy.prepareStatement(
						"SELECT schema_name, table_name, referenced_table FROM duckdb_constraints() WHERE database_name = ? AND constraint_type = 'FOREIGN KEY'")) {
					query.setString(1, snapshot);
					try (var rows = query.executeQuery()) {
						while (rows.next()) {
							final String child = dialect.getObjectFullName(snapshot, rows.getString(1),
									rows.getString(2));
							final String parent = dialect.getObjectFullName(snapshot, rows.getString(1),
									rows.getString(3));
							if (!child.equals(parent))
								children.computeIfAbsent(parent, ignored -> new java.util.ArrayList<>()).add(child);
						}
					}
				}
				final var dropped = new java.util.HashSet<String>();
				for (final String table : tables)
					dropTable(copy, table, children, dropped, new java.util.HashSet<>());
				final String path = directory.toString().replace('\\', '/').replace("'", "''");
				execute(copy, "EXPORT DATABASE " + dialect.quote(snapshot) + " TO '" + path + "'");
				final Map<Key, String> definitions = new HashMap<>();
				for (final var part : dialect.createSqlSplitter()
						.parse(Files.readString(directory.resolve("schema.sql"), StandardCharsets.UTF_8))) {
					if (part.getTextType().isComment())
						continue;
					final var macro = DuckDbDdlParser.exportedMacro(part.getText());
					if (macro == null)
						continue;
					final String sql = "CREATE MACRO "
							+ dialect.getObjectFullName(catalog, macro.schema(), macro.name())
							+ macro.signatureAndBody();
					if (definitions.put(new Key(macro.schema(), macro.name(), macro.table()), sql) != null)
						throw new SQLException(
								"Duplicate exported DuckDB macro: " + macro.schema() + "." + macro.name());
				}
				return definitions;
			} catch (SQLException | IOException | RuntimeException | Error e) {
				failure = e;
				if (e instanceof IOException)
					throw new SQLException("Cannot read exported DuckDB schema", e);
				if (e instanceof SQLException sql)
					throw sql;
				if (e instanceof RuntimeException runtime)
					throw runtime;
				throw (Error) e;
			} finally {
				if (attached) {
					try {
						execute(copy, "DETACH " + dialect.quote(snapshot));
					} catch (SQLException e) {
						if (failure != null)
							failure.addSuppressed(e);
						else
							throw e;
					}
				}
			}
		} catch (SQLException | RuntimeException | Error e) {
			failure = e;
			throw e;
		} finally {
			try (var files = Files.walk(directory)) {
				for (final Path path : files.sorted(Comparator.reverseOrder()).toList())
					Files.delete(path);
			} catch (IOException e) {
				if (failure != null)
					failure.addSuppressed(e);
				else
					throw new SQLException("Cannot remove temporary DuckDB macro export", e);
			}
		}
	}

	private static void execute(final Connection connection, final String sql) throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private static void dropTable(Connection connection, String table, Map<String, java.util.List<String>> children,
			java.util.Set<String> dropped, java.util.Set<String> visiting) throws SQLException {
		if (dropped.contains(table))
			return;
		if (!visiting.add(table))
			throw new SQLException("Cyclic foreign keys in DuckDB schema snapshot");
		for (final String child : children.getOrDefault(table, java.util.List.of()))
			dropTable(connection, child, children, dropped, visiting);
		execute(connection, "DROP TABLE IF EXISTS " + table + " CASCADE");
		visiting.remove(table);
		dropped.add(table);
	}
}
