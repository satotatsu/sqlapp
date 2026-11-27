/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.util.CommonUtils;

/** Uses Phoenix's optional key direction field in the existing JDBC result. */
public class PhoenixPrimaryKeyReader extends UniqueConstraintReader {
	public PhoenixPrimaryKeyReader(Dialect dialect) {
		super(dialect);
	}

	private record Identity(String catalog, String schema, String table) {
	}

	private record Key(UniqueConstraint constraint, SortedMap<Integer, ReferenceColumn> columns) {
	}

	@Override
	protected List<UniqueConstraint> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo version) {
		final Map<Identity, Key> keys = new LinkedHashMap<>();
		try (var rs = new ExResultSet(connection.getMetaData().getPrimaryKeys(
				CommonUtils.coalesce(CommonUtils.emptyToNull(getCatalogName(context)),
						CommonUtils.emptyToNull(getCatalogName())),
				CommonUtils.coalesce(CommonUtils.emptyToNull(getSchemaName(context)),
						CommonUtils.emptyToNull(getSchemaName())),
				CommonUtils.coalesce(CommonUtils.emptyToNull(getTableName(context)),
						CommonUtils.emptyToNull(getObjectName()))))) {
			while (rs.next()) {
				final var identity = new Identity(rs.getString("TABLE_CAT"), rs.getString("TABLE_SCHEM"),
						rs.getString("TABLE_NAME"));
				if (identity.table() == null || identity.table().isBlank())
					throw new IllegalStateException("Missing Phoenix primary-key table identity: " + identity);
				final Key key = keys.computeIfAbsent(identity, id -> {
					final var constraint = new UniqueConstraint();
					constraint.setPrimaryKey(true).setCatalogName(id.catalog()).setSchemaName(id.schema())
							.setTableName(id.table());
					return new Key(constraint, new TreeMap<>());
				});
				final String name = rs.getString("PK_NAME");
				if (!key.columns().isEmpty() && !Objects.equals(key.constraint().getName(), name))
					throw new IllegalStateException("Inconsistent Phoenix primary-key names for " + identity);
				key.constraint().setName(name);
				final int ordinal = rs.getInt("KEY_SEQ");
				if (ordinal <= 0)
					throw new IllegalStateException(
							"Invalid Phoenix primary-key sequence for " + identity + ": " + ordinal);
				final String columnName = rs.getString("COLUMN_NAME");
				if (columnName == null || columnName.isBlank())
					throw new IllegalStateException(
							"Missing Phoenix primary-key column for " + identity + " at sequence " + ordinal);
				final var column = new ReferenceColumn(columnName);
				final String direction = rs.getString("ASC_OR_DESC");
				if (direction != null && !direction.isBlank()) {
					final var order = Order.parse(direction.strip().toUpperCase(Locale.ROOT));
					if (order == null)
						throw new IllegalStateException("Unknown Phoenix primary-key direction: " + direction);
					column.setOrder(order);
				}
				if (key.columns().putIfAbsent(ordinal, column) != null)
					throw new IllegalStateException(
							"Duplicate Phoenix primary-key sequence for " + identity + ": " + ordinal);
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Could not read Phoenix primary keys", e);
		}
		final List<UniqueConstraint> result = new ArrayList<>();
		for (var key : keys.values()) {
			int expected = 1;
			final Set<String> names = new HashSet<>();
			for (var column : key.columns().entrySet()) {
				if (column.getKey() != expected++)
					throw new IllegalStateException(
							"Incomplete Phoenix primary-key sequence: " + key.constraint().getTableName());
				if (!names.add(column.getValue().getName()))
					throw new IllegalStateException("Repeated Phoenix primary-key column for "
							+ key.constraint().getTableName() + ": " + column.getValue().getName());
				key.constraint().getColumns().add(column.getValue());
			}
			result.add(key.constraint());
		}
		return result;
	}
}
