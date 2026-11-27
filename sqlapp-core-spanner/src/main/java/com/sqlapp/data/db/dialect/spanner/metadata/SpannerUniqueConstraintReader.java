/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-spanner.
 *
 * sqlapp-core-spanner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-spanner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-spanner.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.spanner.metadata;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.HashSet;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.UniqueConstraint;

/**
 * Spanner Unique Constraint Reader
 * 
 * @author satoh
 * 
 */
public class SpannerUniqueConstraintReader extends UniqueConstraintReader {
	private record Identity(String catalog, String schema, String table, String name) {
	}

	private record Key(UniqueConstraint constraint, SortedMap<Long, ReferenceColumn> columns) {
	}

	public SpannerUniqueConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<UniqueConstraint> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final Map<Identity, Key> map = new LinkedHashMap<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalogName = getString(rs, TABLE_CATALOG);
				String schemaName = getString(rs, TABLE_SCHEMA);
				String tableName = getString(rs, TABLE_NAME);
				String constraint_name = getString(rs, CONSTRAINT_NAME);
				final String columnName = getString(rs, COLUMN_NAME);
				if (tableName == null || tableName.isBlank() || constraint_name == null || constraint_name.isBlank()
						|| columnName == null || columnName.isBlank())
					throw new IllegalArgumentException(
							"Spanner primary-key metadata requires table, key and column names");
				final var identity = new Identity(catalogName, schemaName, tableName, constraint_name);
				final Key key = map.computeIfAbsent(identity, id -> {
					final var c = new UniqueConstraint(constraint_name, true);
					c.setPrimaryKey(true);
					c.setCatalogName(catalogName);
					c.setSchemaName(schemaName);
					c.setTableName(tableName);
					return new Key(c, new TreeMap<>());
				});
				final Long ordinal = getLong(rs, "ordinal_position");
				if (ordinal == null || ordinal <= 0 || ordinal > Integer.MAX_VALUE)
					throw new IllegalArgumentException(
							"Invalid Spanner primary-key ordinal for " + identity + ": " + ordinal);
				final var order = SpannerUtils.parseOrder(getString(rs, "column_ordering"));
				if (order == null)
					throw new IllegalArgumentException("Unknown Spanner primary-key ordering for " + identity);
				if (key.columns().putIfAbsent(ordinal, new ReferenceColumn(columnName).setOrder(order)) != null)
					throw new IllegalArgumentException(
							"Duplicate Spanner primary-key ordinal for " + identity + ": " + ordinal);
			}
		});
		for (var key : map.values()) {
			long expected = 1;
			final var names = new HashSet<String>();
			for (var column : key.columns().entrySet()) {
				if (column.getKey() != expected++)
					throw new IllegalArgumentException(
							"Incomplete Spanner primary-key sequence: " + key.constraint().getTableName());
				if (!names.add(column.getValue().getName()))
					throw new IllegalArgumentException(
							"Repeated Spanner primary-key column: " + column.getValue().getName());
				key.constraint().getColumns().add(column.getValue());
			}
		}
		return map.values().stream().map(Key::constraint).toList();
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("uniqueConstraints.sql");
	}

}
