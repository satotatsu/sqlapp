/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.jdbc.metadata;

import static com.sqlapp.util.CommonUtils.emptyToNull;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.DbUtils.close;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Collection;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;

public class JdbcMetadataUtils {

	/** Converts the standard JDBC datetime display width to fractional-second precision.
	 * Vendor declarations (for example DATETIME qualifiers) remain the owning reader's responsibility.
	 */
	public static Long columnPrecision(int sqlType, String typeName, Long displaySize) throws SQLException {
		if (typeName == null || !typeName.matches("(?is)(TIME|TIMESTAMP)(?:\\s*\\(\\d+\\))?(?:\\s+(?:WITH|WITHOUT)\\s+TIME\\s+ZONE)?")) {
			return displaySize;
		}
		int base = switch (sqlType) {
		case java.sql.Types.TIME -> 8;
		case java.sql.Types.TIMESTAMP -> 19;
		case java.sql.Types.TIME_WITH_TIMEZONE -> 14;
		case java.sql.Types.TIMESTAMP_WITH_TIMEZONE -> 25;
		default -> -1;
		};
		if (base < 0) return displaySize;
		if (displaySize != null && displaySize == base) return 0L;
		if (displaySize != null && displaySize >= base + 2 && displaySize <= base + 10) {
			return displaySize - base - 1;
		}
		throw new SQLException("Cannot resolve fractional-second precision for " + typeName + " from JDBC COLUMN_SIZE="
				+ displaySize + "; use a vendor metadata reader for this datetime representation");
	}

	static final String SELECTED_TABLES = JdbcMetadataUtils.class.getName() + ".tables";

	@FunctionalInterface
	interface TableMetadataQuery<T> {
		List<T> read(ParametersContext context);
	}

	/**
	 * JDBC exposes index and key metadata only for one exact table at a time. Reuse
	 * the owning reader's table list to avoid repeating discovery queries.
	 */
	static <T> List<T> readTables(Connection connection, ParametersContext context, String catalog, String schema,
			String tableName, TableMetadataQuery<T> query) {
		List<Table> tables = list();
		Object selected = context.get(SELECTED_TABLES);
		if (selected instanceof Collection<?> values) {
			for (Object value : values) {
				tables.add((Table) value);
			}
		} else if (tableName != null && !isSelection(context.get("tableName"))
				&& !isSelection(context.get("catalogName")) && !isSelection(context.get("schemaName"))) {
			// A standalone exact-table call may intentionally leave catalog/schema
			// unrestricted.
			return query.read(context);
		} else {
			try {
				for (Table table : getMetadata(connection, isSelection(context.get("catalogName")) ? null : catalog,
						null, null, new String[] { "TABLE", "SYSTEM TABLE" })) {
					if (matches(context.getOrDefault("catalogName", catalog), table.getCatalogName())
							&& matches(context.getOrDefault("schemaName", schema), table.getSchemaName())
							&& matches(context.get("tableName"), table.getName())) {
						tables.add(table);
					}
				}
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}
		List<T> result = list();
		for (Table table : tables) {
			ParametersContext tableContext = context.clone();
			tableContext.remove(SELECTED_TABLES);
			// Empty names mean "without a catalog/schema", not an unrestricted search.
			tableContext.put("catalogName", table.getCatalogName() == null ? "" : table.getCatalogName());
			tableContext.put("schemaName", table.getSchemaName() == null ? "" : table.getSchemaName());
			tableContext.put("tableName", table.getName());
			result.addAll(query.read(tableContext));
		}
		return result;
	}

	private static boolean matches(Object selection, String name) {
		if (selection instanceof Collection<?> values) {
			return values.stream().anyMatch(value -> matches(value, name));
		}
		if (selection != null && selection.getClass().isArray()) {
			for (int i = 0; i < java.lang.reflect.Array.getLength(selection); i++) {
				if (matches(java.lang.reflect.Array.get(selection, i), name)) {
					return true;
				}
			}
			return false;
		}
		return selection == null || java.util.Objects.equals(emptyToNull((String) selection), emptyToNull(name));
	}

	private static boolean isSelection(Object value) {
		return value instanceof Collection<?> || value != null && value.getClass().isArray();
	}

	public static List<Table> getMetadata(Connection connection, String catalogName, String schemaName,
			String tableName, String[] tableTypes) throws SQLException {
		ResultSet resultSet = null;
		List<Table> result = list();
		try {
			DatabaseMetaData databaseMetaData = connection.getMetaData();
			resultSet = databaseMetaData.getTables(emptyToNull(catalogName), emptyToNull(schemaName),
					emptyToNull(tableName), tableTypes);
			while (resultSet.next()) {
				String remarks = resultSet.getString("REMARKS");
				String tableType = resultSet.getString("TABLE_TYPE");
				String name = resultSet.getString("TABLE_NAME");
				Table table;
				if ("VIEW".equals(tableType)) {
					table = new View(name);
				} else {
					table = new Table(name);
				}
				table.setCatalogName(resultSet.getString("TABLE_CAT"));
				table.setSchemaName(resultSet.getString("TABLE_SCHEM"));
				table.setRemarks(remarks);
				result.add(table);
			}
			return result;
		} finally {
			close(resultSet);
		}
	}

	/**
	 * ResultSetのメタデータのカラム情報を読み込みます
	 * 
	 * @param resultSet
	 * @param dialect   DB方言
	 * @throws SQLException
	 */
	public static List<Column> getColumnMetadata(ResultSet resultSet, Dialect dialect) throws SQLException {
		ResultSetMetaData metaData = resultSet.getMetaData();
		int colCount = metaData.getColumnCount();
		List<Column> result = list(colCount);
		for (int i = 1; i <= colCount; i++) {
			String colName = metaData.getColumnName(i);
			int sqlType = metaData.getColumnType(i);
			String productDataType = metaData.getColumnTypeName(i);
			long precision = metaData.getPrecision(i);
			int nullable = metaData.isNullable(i);
			int scale = metaData.getScale(i);
			boolean allowDBNull = false;
			if (nullable != ResultSetMetaData.columnNullableUnknown) {
				if (nullable == ResultSetMetaData.columnNullable) {
					allowDBNull = true;
				}
			}
			boolean autoIncrement = metaData.isAutoIncrement(i);
			Column column = new Column(colName);
			// ResultSetMetaData.getScale directly reports fractional-second precision for datetime values.
			Long length = isTemporal(sqlType) ? Long.valueOf(scale) : Long.valueOf(precision);
			dialect.setDbType(sqlType, productDataType, length, scale, column);
			column.setNullable(allowDBNull);
			column.setIdentity(autoIncrement);
			result.add(column);
		}
		return result;
	}

	static boolean isTemporal(int sqlType) {
		return sqlType == java.sql.Types.TIME || sqlType == java.sql.Types.TIMESTAMP
				|| sqlType == java.sql.Types.TIME_WITH_TIMEZONE || sqlType == java.sql.Types.TIMESTAMP_WITH_TIMEZONE;
	}
}
