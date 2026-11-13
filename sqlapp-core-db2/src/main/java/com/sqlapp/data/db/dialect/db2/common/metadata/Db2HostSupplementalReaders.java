/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.common.Db2HostIndexes;
import com.sqlapp.data.db.metadata.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Shared bulk mappings; all catalog SQL stays in the owning host product
 * module.
 */
final class Db2HostSupplementalReaders {

	private Db2HostSupplementalReaders() {
	}

	static class Views extends ViewReader {
		private final Class<?> resources;

		Views(Dialect dialect, Class<?> resources) {
			super(dialect);
			this.resources = resources;
		}

		@Override
		protected List<Table> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
			List<Table> result = new ArrayList<>();
			execute(connection, getSqlNodeCache(resources).getString("views.sql"), context, new ResultSetNextHandler() {
				@Override
				public void handleResultSetNext(ExResultSet rs) throws SQLException {
					var view = new View(id(rs, "TABLE_NAME"));
					view.setSchemaName(id(rs, "SCHEMA_NAME"));
					String text = requiredText(rs, "VIEW_TEXT");
					if ("D".equals(id(rs, "TEXT_KIND"))) {
						if (!text.stripLeading().matches("(?is)CREATE\\s+(?:OR\\s+REPLACE\\s+)?VIEW\\b.*"))
							throw new SQLException(
									"View catalog does not contain a complete CREATE VIEW: " + view.getName());
						view.setDefinition(text);
					} else {
						view.setStatement(text);
						String check = id(rs, "CHECK_OPTION");
						if (check == null || !List.of("N", "Y", "C").contains(check))
							throw new SQLException("Unknown view check option: " + check);
						view.getSpecifics().put("VIEWCHECK", "Y".equals(check) ? "L" : check);
					}
					view.getSpecifics().put("QUALIFIER", view.getSchemaName());
					String path = rs.getString("SQL_PATH");
					if (path != null && !path.isBlank())
						view.getSpecifics().put("FUNC_PATH", path);
					view.setRemarks(rs.getString("REMARKS"));
					result.add(view);
				}
			});
			return result;
		}

		@Override
		protected ColumnReader newColumnReader() {
			return getDialect().getCatalogReader().getSchemaReader().getTableReader().getColumnReader();
		}

		@Override
		protected IndexReader newIndexReader() {
			return null;
		}

		@Override
		protected ExcludeConstraintReader newExcludeConstraintReader() {
			return null;
		}
	}

	static class Checks extends CheckConstraintReader {
		private final Class<?> resources;

		Checks(Dialect dialect, Class<?> resources) {
			super(dialect);
			this.resources = resources;
		}

		@Override
		protected List<CheckConstraint> doGetAll(Connection connection, ParametersContext context,
				ProductVersionInfo version) {
			List<CheckConstraint> result = new ArrayList<>();
			execute(connection, getSqlNodeCache(resources).getString("checks.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							String unsupported = id(rs, "UNSUPPORTED_CHECK");
							if (unsupported != null && !unsupported.isBlank())
								throw new SQLException("Unsupported check constraint attributes: " + unsupported);
							var check = new CheckConstraint(id(rs, "CONSTRAINT_NAME"), requiredText(rs, "CHECK_TEXT"));
							check.setSchemaName(id(rs, "SCHEMA_NAME"));
							check.setTableName(id(rs, "TABLE_NAME"));
							check.setEnable("Y".equals(id(rs, "ENFORCED")));
							result.add(check);
						}
					});
			return result;
		}
	}

	static class Sequences extends SequenceReader {
		private final Class<?> resources;

		Sequences(Dialect dialect, Class<?> resources) {
			super(dialect);
			this.resources = resources;
		}

		@Override
		protected List<Sequence> doGetAll(Connection connection, ParametersContext context,
				ProductVersionInfo version) {
			List<Sequence> result = new ArrayList<>();
			execute(connection, getSqlNodeCache(resources).getString("sequences.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							var sequence = new Sequence(id(rs, "SEQUENCE_NAME"));
							sequence.setSchemaName(id(rs, "SCHEMA_NAME"));
							String type = id(rs, "SEQUENCE_TYPE");
							if (type != null) {
								if (!List.of("SMALLINT", "INTEGER", "BIGINT", "DECIMAL", "NUMERIC").contains(type))
									throw new SQLException("Unsupported sequence type: " + type);
								sequence.setDataType(switch (type) {
								case "SMALLINT" -> com.sqlapp.data.db.datatype.DataType.SMALLINT;
								case "INTEGER" -> com.sqlapp.data.db.datatype.DataType.INT;
								case "BIGINT" -> com.sqlapp.data.db.datatype.DataType.BIGINT;
								case "NUMERIC" -> com.sqlapp.data.db.datatype.DataType.NUMERIC;
								default -> com.sqlapp.data.db.datatype.DataType.DECIMAL;
								});
							} else {
								sequence.setDataType((com.sqlapp.data.db.datatype.DataType) null);
								// z/OS exposes internal IDs, not a documented built-in type name.
								sequence.getSpecifics().put("DB2_HOST_DATATYPE_ID", requiredText(rs, "NATIVE_TYPE_ID"));
							}
							sequence.setPrecision(rs.getInteger("TYPE_PRECISION"));
							sequence.setScale(0);
							sequence.setStartValue(rs.getBigDecimal("START_VALUE"));
							sequence.setIncrementBy(rs.getBigDecimal("INCREMENT_VALUE"));
							sequence.setMinValue(rs.getBigDecimal("MINIMUM_VALUE"));
							sequence.setMaxValue(rs.getBigDecimal("MAXIMUM_VALUE"));
							if (sequence.getStartValue() == null || sequence.getIncrementBy() == null
									|| sequence.getMinValue() == null || sequence.getMaxValue() == null)
								throw new SQLException("Incomplete sequence bounds: " + sequence.getName());
							sequence.setCycle(flag(rs, "CYCLE_FLAG"));
							sequence.setOrder(flag(rs, "ORDER_FLAG"));
							Integer cache = rs.getInteger("CACHE_SIZE");
							if (cache == null || cache < 0)
								throw new SQLException("Invalid sequence cache: " + cache);
							sequence.setCacheSize(cache);
							sequence.setCache(cache > 0);
							sequence.setRemarks(rs.getString("REMARKS"));
							result.add(sequence);
						}
					});
			return result;
		}
	}

	static class Indexes extends IndexReader {
		private final Class<?> resources;

		Indexes(Dialect dialect, Class<?> resources) {
			super(dialect);
			this.resources = resources;
		}

		@Override
		protected List<Index> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
			Map<List<String>, Index> result = new LinkedHashMap<>();
			Map<List<String>, Integer> keyCounts = new LinkedHashMap<>();
			execute(connection, getSqlNodeCache(resources).getString("indexes.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							String unsupported = id(rs, "UNSUPPORTED_INDEX");
							if (unsupported != null && !unsupported.isBlank())
								throw new SQLException("Unsupported index attributes: " + unsupported + " ("
										+ id(rs, "INDEX_NAME") + ")");
							String schema = id(rs, "SCHEMA_NAME"), table = id(rs, "TABLE_NAME"),
									name = id(rs, "INDEX_NAME");
							String owner = id(rs, "INDEX_SCHEMA");
							var key = List.of(schema, table, owner, name);
							Integer keyCount = rs.getInteger("KEY_COUNT"), position = rs.getInteger("KEY_POSITION");
							if (keyCount == null || keyCount < 1 || position == null)
								throw new SQLException("Missing index key count/position: " + name);
							Index index = result.get(key);
							if (index == null) {
								index = new Index(name);
								index.setSchemaName(schema);
								index.setTableName(table);
								index.setUnique(flag(rs, "UNIQUE_FLAG"));
								index.getSpecifics().put("INDEX_SCHEMA", owner);
								if ("Y".equals(optional(rs, "UNIQUE_WHERE_NOT_NULL"))) {
									if (!index.isUnique())
										throw new SQLException(
												"UNIQUE WHERE NOT NULL requires a unique index: " + name);
									index.getSpecifics().put(Db2HostIndexes.UNIQUE_WHERE_NOT_NULL, "Y");
								}
								String where = optionalText(rs, "WHERE_TEXT");
								if (where != null)
									index.setWhere(where);
								String cluster = optional(rs, "CLUSTER_FLAG");
								if (cluster != null && !cluster.isBlank()) {
									if (!List.of("Y", "N").contains(cluster))
										throw new SQLException("Invalid index CLUSTER flag: " + cluster);
									if ("Y".equals(cluster))
										index.getSpecifics().put(Db2HostIndexes.CLUSTER, "Y");
								}
								String partitioned = optional(rs, "PARTITIONED_FLAG");
								if (partitioned != null && !partitioned.isBlank()) {
									if (!List.of("Y", "N").contains(partitioned))
										throw new SQLException("Unknown index partitioning state: " + partitioned);
									index.getSpecifics().put(Db2HostIndexes.PARTITIONED, partitioned);
								}
								index.setRemarks(rs.getString("REMARKS"));
								result.put(key, index);
								keyCounts.put(key, keyCount);
							}
							int read = index.getColumns().size() + index.getIncludes().size();
							if (!keyCount.equals(keyCounts.get(key)) || position != read + 1)
								throw new SQLException("Incomplete or unordered index keys: " + name);
							String order = id(rs, "COLUMN_ORDER"), column = id(rs, "COLUMN_NAME");
							String expression = optionalText(rs, "KEY_EXPRESSION");
							if ("Y".equals(optional(rs, "KEY_INCLUDE"))) {
								if (!index.isUnique() || expression != null || column == null || column.isBlank())
									throw new SQLException("Unsupported index INCLUDE key: " + name);
								index.getIncludes().add(new Column(column)).setIncludedColumn(true);
								return;
							}
							if (!index.getIncludes().isEmpty())
								throw new SQLException("Index key follows an INCLUDE column: " + name);
							if (order == null || !List.of("A", "D").contains(order))
								throw new SQLException("Unsupported index key order: " + order + " (" + name + ")");
							if (expression != null) {
								// Index-on-expression key; the native text is replayed verbatim.
								var reference = new com.sqlapp.data.schemas.ReferenceColumn(expression);
								reference.setStatement(expression);
								reference.setOrder("A".equals(order) ? Order.Asc : Order.Desc);
								index.getColumns().add(reference);
								return;
							}
							if (column == null || column.isBlank())
								throw new SQLException("Index key has neither a column nor an expression: " + name);
							index.getColumns().add(new Column(column), "A".equals(order) ? Order.Asc : Order.Desc);
						}
					});
			for (var entry : result.entrySet())
				if (entry.getValue().getColumns().size() + entry.getValue().getIncludes().size() != keyCounts
						.get(entry.getKey()) || entry.getValue().getColumns().isEmpty())
					throw new IllegalStateException("Incomplete index keys: " + entry.getValue().getName());
			return new ArrayList<>(result.values());
		}
	}

	private static String requiredText(ExResultSet rs, String name) throws SQLException {
		String value = rs.getString(name);
		if (value == null || value.isBlank() || value.indexOf('\0') >= 0)
			throw new SQLException("Missing or truncated catalog text: " + name);
		return value;
	}

	/**
	 * Reads a column added by later catalog SQL revisions. Callers validate the
	 * value; absence means the attribute is not reported by the resource.
	 */
	static String optional(ExResultSet rs, String name) throws SQLException {
		return has(rs, name) ? id(rs, name) : null;
	}

	/** Optional catalog text; blank becomes null, embedded NUL is rejected. */
	static String optionalText(ExResultSet rs, String name) throws SQLException {
		if (!has(rs, name))
			return null;
		String value = rs.getString(name);
		if (value == null || value.isBlank())
			return null;
		if (value.indexOf('\0') >= 0)
			throw new SQLException("Truncated catalog text: " + name);
		return value.strip();
	}

	static boolean has(ExResultSet rs, String name) throws SQLException {
		var metadata = rs.getMetaData();
		for (int i = 1; i <= metadata.getColumnCount(); i++)
			if (name.equalsIgnoreCase(metadata.getColumnLabel(i)))
				return true;
		return false;
	}

	private static String id(ExResultSet rs, String name) throws SQLException {
		String value = rs.getString(name);
		return value == null ? null : value.stripTrailing();
	}

	private static boolean flag(ExResultSet rs, String name) throws SQLException {
		String value = id(rs, name);
		if (value == null || !List.of("Y", "N").contains(value))
			throw new SQLException("Invalid catalog flag " + name + ": " + value);
		return "Y".equals(value);
	}
}
