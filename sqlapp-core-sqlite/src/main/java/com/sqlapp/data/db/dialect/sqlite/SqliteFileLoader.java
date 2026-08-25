/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

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
		final Path normalized = normalize(file);
		final SQLiteDataSource dataSource = createDataSource(normalized);
		try (var connection = dataSource.getConnection()) {
			final Schema schema = SchemaUtils.getSchema(connection, "main")
					.orElseThrow(() -> new IOException(
							"SQLite schema not found: " + normalized));
			for (final Table table : schema.getTables()) {
				table.setRowIteratorHandler(
						new JdbcDynamicRowIteratorHandler(dataSource));
			}
			return schema;
		} catch (SQLException e) {
			throw new IOException("Failed to read SQLite file: " + normalized,
					e);
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

	private static Path normalize(final Path file) throws IOException {
		final Path normalized = file.toAbsolutePath().normalize();
		if (!Files.isRegularFile(normalized)) {
			throw new IOException("SQLite file not found: " + normalized);
		}
		return normalized;
	}

	private static SQLiteDataSource createDataSource(final Path file) {
		final SQLiteConfig config = new SQLiteConfig();
		config.setReadOnly(true);
		config.enforceForeignKeys(true);
		final SQLiteDataSource dataSource = new SQLiteDataSource(config);
		dataSource.setUrl("jdbc:sqlite:" + file);
		return dataSource;
	}
}
