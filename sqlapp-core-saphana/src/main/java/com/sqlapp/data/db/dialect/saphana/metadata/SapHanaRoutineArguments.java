/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.metadata;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.jdbc.ExResultSet;

/** Folds a bulk parameter/column join without issuing per-parameter queries. */
final class SapHanaRoutineArguments {
	private record Key(String schema, String routine, String argument) {
	}

	private final Dialect dialect;
	private final Map<Key, NamedArgument> arguments = new LinkedHashMap<>();
	private final Map<Key, List<Column>> tableColumns = new LinkedHashMap<>();

	SapHanaRoutineArguments(final Dialect dialect) {
		this.dialect = dialect;
	}

	void add(final NamedArgument argument, final ExResultSet rs) throws SQLException {
		final Key key = new Key(argument.getSchemaName(), argument.getRoutineName(), argument.getName());
		arguments.putIfAbsent(key, argument);
		if (rs.getString("TABLE_COLUMN_NAME") != null) {
			tableColumns.computeIfAbsent(key, ignored -> new ArrayList<>()).add(readColumn(dialect, rs));
		}
	}

	static Column readColumn(final Dialect dialect, final ExResultSet rs) throws SQLException {
		final Column column = new Column(rs.getString("TABLE_COLUMN_NAME"));
		final int value = rs.getInt("TABLE_COLUMN_SCALE");
		final Integer scale = rs.wasNull() ? null : value;
		dialect.setDbType(rs.getString("TABLE_COLUMN_DATA_TYPE_NAME"), rs.getLong("TABLE_COLUMN_LENGTH"), scale,
				column);
		column.setNullable(!"FALSE".equalsIgnoreCase(rs.getString("TABLE_COLUMN_IS_NULLABLE")));
		return column;
	}

	List<NamedArgument> finish() {
		for (var entry : tableColumns.entrySet()) {
			final var builder = dialect.createSqlBuilder()._add("TABLE (");
			int position = 0;
			for (Column column : entry.getValue()) {
				builder.comma(position++ > 0).name(column).space().addTypeDefinition(column);
			}
			builder._add(")");
			arguments.get(entry.getKey()).setDataType(DataType.OTHER).setDataTypeName(builder.toString());
		}
		return new ArrayList<>(arguments.values());
	}
}
