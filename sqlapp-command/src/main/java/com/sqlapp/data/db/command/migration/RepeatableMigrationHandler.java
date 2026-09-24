/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/** Persists the latest checksum of each repeatable migration. */
final class RepeatableMigrationHandler {
	static final String EXECUTION_ID_COLUMN = "execution_id";
	static final String NAME_COLUMN = "migration_name";
	static final String CHECKSUM_COLUMN = "checksum";
	static final String APPLIED_BY_COLUMN = "applied_by";
	static final String APPLIED_AT_COLUMN = "applied_at";

	Table definition(final String versionTableName) {
		final String[] parts = versionTableName.split("\\.");
		final Table table = new Table();
		table.setName(parts[parts.length - 1] + "_repeatable");
		if (parts.length > 1) {
			table.setSchemaName(parts[parts.length - 2]);
		}
		final Column executionId = new Column(EXECUTION_ID_COLUMN).setDataType(DataType.BIGINT);
		table.getColumns().add(executionId);
		table.getColumns().add(new Column(NAME_COLUMN).setDataType(DataType.NVARCHAR).setLength(255));
		table.getColumns().add(new Column(CHECKSUM_COLUMN).setDataType(DataType.NVARCHAR).setLength(71));
		table.getColumns().add(new Column(APPLIED_BY_COLUMN).setDataType(DataType.NVARCHAR).setLength(255));
		table.getColumns().add(new Column(APPLIED_AT_COLUMN).setDataType(DataType.DATETIME));
		table.getConstraints().addPrimaryKeyConstraint(table.getName() + "_PK", executionId);
		return table;
	}

	Map<String, String> load(final Connection connection, final Dialect dialect, final Table table)
			throws SQLException {
		final Map<String, String> result = new LinkedHashMap<>();
		final String sql = "SELECT " + dialect.quote(NAME_COLUMN) + ", " + dialect.quote(CHECKSUM_COLUMN)
				+ " FROM " + qualified(dialect, table) + " ORDER BY " + dialect.quote(EXECUTION_ID_COLUMN);
		try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				result.put(rows.getString(1), rows.getString(2));
			}
		}
		return result;
	}

	void record(final Connection connection, final Dialect dialect, final Table table,
			final RepeatableMigrationFile migration) throws SQLException {
		final long executionId = nextExecutionId(connection, dialect, table);
		final AbstractSqlBuilder<?> builder = dialect.createSqlBuilder();
		builder.insert().into().space().name(table).space()._add("(")
				.name(EXECUTION_ID_COLUMN).comma().name(NAME_COLUMN).comma().name(CHECKSUM_COLUMN).comma()
				.name(APPLIED_BY_COLUMN).comma().name(APPLIED_AT_COLUMN)._add(")").values().space()
				._add("(?,?,?,?,?)");
		try (PreparedStatement insert = connection.prepareStatement(builder.toString())) {
			insert.setLong(1, executionId);
			insert.setString(2, migration.name());
			insert.setString(3, migration.checksum());
			insert.setString(4, connection.getMetaData().getUserName());
			insert.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
			insert.executeUpdate();
		}
	}

	private long nextExecutionId(final Connection connection, final Dialect dialect, final Table table)
			throws SQLException {
		final String sql = "SELECT MAX(" + dialect.quote(EXECUTION_ID_COLUMN) + ") FROM "
				+ qualified(dialect, table);
		try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet row = statement.executeQuery()) {
			return row.next() ? row.getLong(1) + 1L : 1L;
		}
	}

	private String qualified(final Dialect dialect, final Table table) {
		return table.getSchemaName() == null ? dialect.quote(table.getName())
				: dialect.quote(table.getSchemaName()) + "." + dialect.quote(table.getName());
	}
}
