/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sybase.
 *
 * sqlapp-core-sybase is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sybase is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sybase.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sybase.metadata;

import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.eqIgnoreCase;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.notZero;
import static com.sqlapp.util.CommonUtils.unwrap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * SQLServerのカラム読み込み
 * 
 * @author satoh
 * 
 */
public class SybaseColumnReader extends ColumnReader {

	protected SybaseColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Column> result = list();
		final java.util.Map<Column, int[]> definitions = new java.util.LinkedHashMap<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Column column = createColumn(rs);
				if (rs.getBoolean("is_computed") && rs.getInt("computed_id") <= 0 && column.getFormula() == null)
					throw new IllegalStateException(
							"Missing SAP ASE computed column source identity: " + column.getName());
				result.add(column);
				definitions.put(column, new int[] { rs.getInt("default_id"), rs.getInt("computed_id") });
			}
		});
		final var ids = new java.util.LinkedHashSet<Integer>();
		definitions.values().forEach(pair -> {
			for (int id : pair)
				if (id > 0)
					ids.add(id);
		});
		if (!ids.isEmpty()) {
			final var parameters = context.clone();
			parameters.put("definitionIds", ids);
			final java.util.Map<Integer, StringBuilder> texts = new java.util.LinkedHashMap<>();
			execute(connection, getSqlNodeCache().getString("columnDefinitions.sql"), parameters,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							final int id = rs.getInt("definition_id");
							texts.computeIfAbsent(id, key -> new StringBuilder())
									.append(SybaseSourceText.required(rs, "definition", "column source " + id));
						}
					});
			definitions.forEach((column, pair) -> {
				if (pair[0] > 0 && !column.isIdentity())
					column.setDefaultValue(SybaseSourceText.defaultExpression(requiredDefinition(texts, pair[0])));
				if (pair[1] > 0)
					column.setFormula(requiredDefinition(texts, pair[1]));
			});
		}
		return result;
	}

	private String requiredDefinition(java.util.Map<Integer, StringBuilder> texts, int id) {
		final var text = texts.get(id);
		if (text == null || text.toString().isBlank())
			throw new IllegalStateException("Missing SAP ASE column source: " + id);
		return text.toString();
	}

	protected SqlNode getSqlSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString(getColumnSqlResource(productVersionInfo));
	}

	static String getColumnSqlResource(final ProductVersionInfo productVersionInfo) {
		return productVersionInfo != null && productVersionInfo.getMajorVersion() != null
				&& productVersionInfo.getMajorVersion() >= 15 ? "columns15.sql" : "columns.sql";
	}

	/**
	 * SQLServer用のカラムコメント設定
	 * 
	 * @param connection
	 * @param columns
	 */
	protected void setColumnComments(Connection connection, ParametersContext context, final List<Column> columns) {
		String schemaName = null;
		String tableName = null;
		for (Column column : columns) {
			if (tableName == null) {
				schemaName = column.getSchemaName();
				tableName = column.getTableName();
				setColumnComments(connection, context, columns, schemaName, tableName);
			} else if (!eq(column.getSchemaName(), schemaName) || !eq(column.getTableName(), tableName)) {
				schemaName = column.getSchemaName();
				tableName = column.getTableName();
				setColumnComments(connection, context, columns, schemaName, tableName);
			}
			schemaName = column.getSchemaName();
			tableName = column.getTableName();
		}
	}

	/**
	 * SQLServer用のカラムコメント設定
	 * 
	 * @param connection
	 * @param columns
	 */
	protected void setColumnComments(Connection connection, ParametersContext context, final List<Column> columns,
			String schemaName, String tableName) {
		SqlNode node = getSqlNodeCache().getString("columnComments.sql");
		context.put(TABLE_NAME, tableName);
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String columnName = getString(rs, "objname");
				String comment = getString(rs, "value");
				for (Column searchColumn : columns) {
					if (!eqIgnoreCase(columnName, searchColumn.getName())) {
						continue;
					}
					searchColumn.setRemarks(comment);
				}
			}
		});
	}

	protected Column createColumn(ExResultSet rs) throws SQLException {
		String productDataType = getString(rs, "type_name");
		Long byteLength = getLong(rs, "max_length");
		Long max_length = SybaseUtils.getMaxLength(productDataType, byteLength);
		Long precision = getLong(rs, "precision");
		Integer scale = getInteger(rs, "scale");
		Column obj = new Column(getString(rs, COLUMN_NAME));
		obj.setNullable(rs.getBoolean("is_nullable"));
		obj.setIdentity(rs.getBoolean("is_identity"));
		final boolean decimal = productDataType != null && java.util.Set.of("NUMERIC", "DECIMAL", "DEC")
				.contains(productDataType.toUpperCase(java.util.Locale.ROOT));
		this.getDialect().setDbType(productDataType, decimal ? precision : notZero(max_length, precision), scale, obj);
		obj.setDefaultValue(unwrap(rs.getString("default_definition"), '(', ')'));
		if (rs.getBoolean("is_computed")) {
			obj.setFormula(rs.getString("computed_definition"));
			obj.setFormulaPersisted(rs.getBoolean("is_computed_persisted"));
		}
		// column.setOctetLength(rs.getInt("CHAR_OCTET_LENGTH"));
		obj.setCatalogName(getString(rs, CATALOG_NAME));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setTableName(getString(rs, TABLE_NAME));
		if (obj.isIdentity()) {
			obj.setIdentityStartValue(rs.getLongValue("ident_seed"));
			obj.setIdentityStep(rs.getLongValue("ident_increment"));
			obj.setIdentityLastValue(rs.getLongValue("ident_current"));
		}
		obj.setCollation(getString(rs, COLLATION_NAME));
		obj.setRemarks(getString(rs, "remarks"));
		return obj;
	}
}
