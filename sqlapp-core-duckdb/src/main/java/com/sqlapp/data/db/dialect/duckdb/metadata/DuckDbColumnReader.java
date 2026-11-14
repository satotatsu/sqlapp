/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.converter.DefaultConverter;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.duckdb.util.DuckDbDdlParser;
import com.sqlapp.data.db.dialect.duckdb.util.DuckDbDdlParser.ColumnDdl;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Reads DuckDB table and view columns from {@code duckdb_columns()}.
 * <p>
 * Generated columns and collations are taken from the table DDL returned by the
 * same query: {@code duckdb_columns()} reports generation expressions as column
 * defaults before DuckDB 2.0 and does not report collations.
 * </p>
 */
public class DuckDbColumnReader extends ColumnReader {

	private static final Pattern DECIMAL = Pattern.compile("(?i)(DECIMAL|NUMERIC)\\s*\\(.*\\)");

	public DuckDbColumnReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final SqlNode node = getSqlNodeCache().getString("columns.sql");
		final List<Column> result = list();
		final Map<List<String>, Map<String, ColumnDdl>> tableColumns = new HashMap<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final String catalogName = getString(rs, TABLE_CATALOG);
				final String schemaName = getString(rs, TABLE_SCHEMA);
				final String tableName = getString(rs, TABLE_NAME);
				final Column column = new Column(getString(rs, COLUMN_NAME));
				column.setDialect(getDialect());
				column.setCatalogName(catalogName);
				column.setSchemaName(schemaName);
				column.setTableName(tableName);
				column.setNullable(rs.getBoolean("is_nullable"));
				setDataType(rs, column);
				final String tableSql = getString(rs, "table_sql");
				final ColumnDdl ddl = tableSql == null ? null
						: parse(tableColumns, Arrays.asList(catalogName, schemaName, tableName), tableSql)
								.get(column.getName());
				if (ddl != null && ddl.generationExpression() != null) {
					column.setFormula(ddl.generationExpression());
				} else {
					column.setDefaultValue(getString(rs, "column_default"));
				}
				if (ddl != null && ddl.collation() != null) {
					column.setCollation(ddl.collation());
				}
				column.setRemarks(getString(rs, REMARKS));
				result.add(column);
			}
		});
		return result;
	}

	private static Map<String, ColumnDdl> parse(final Map<List<String>, Map<String, ColumnDdl>> cache,
			final List<String> key, final String tableSql) throws SQLException {
		Map<String, ColumnDdl> columns = cache.get(key);
		if (columns == null) {
			try {
				columns = DuckDbDdlParser.columns(tableSql);
			} catch (final IllegalArgumentException e) {
				throw new SQLException("Failed to read DuckDB table DDL: " + String.join(".", key), e);
			}
			cache.put(key, columns);
		}
		return columns;
	}

	/**
	 * Keeps values read by the DuckDB type handlers unchanged when they are put
	 * into rows: the default TIME conversion to {@link java.sql.Time} drops
	 * microseconds, and the default INTERVAL conversion does not keep every
	 * interval.
	 *
	 * @param column column
	 */
	protected void keepValuePrecision(final Column column) {
		if (column.getDataType() == DataType.TIME) {
			column.setConverter(Converters.getDefault().getConverter(LocalTime.class));
		} else if (column.getDataType() == DataType.INTERVAL) {
			column.setConverter(new DefaultConverter());
		}
	}

	protected void setDataType(final ExResultSet rs, final Column column) throws SQLException {
		final String dataType = getString(rs, DATA_TYPE);
		if (dataType != null && DECIMAL.matcher(dataType.trim()).matches()) {
			getDialect().setDbType(dataType, getLong(rs, "numeric_precision"), getInteger(rs, "numeric_scale"), column);
			return;
		}
		getDialect().setDbType(dataType, null, null, column);
		keepValuePrecision(column);
		final List<String> values = DuckDbDdlParser.enumValues(dataType);
		if (values != null) {
			column.getValues().clear();
			column.getValues().addAll(values);
		}
	}
}
