/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.mysql.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.CharacterSemantics;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DbUtils;

public class MySqlColumnReader extends ColumnReader {

	protected MySqlColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(final Connection connection,
			ParametersContext context, ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Column> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Column column = createColumn(connection, rs);
				result.add(column);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("columns.sql");
	}

	protected Column createColumn(final Connection connection, ExResultSet rs) throws SQLException {
		Column column = createColumn(getString(rs, COLUMN_NAME));
		column.setCatalogName(getString(rs, TABLE_CATALOG));
		column.setSchemaName(getString(rs, TABLE_SCHEMA));
		column.setTableName(getString(rs, TABLE_NAME));
		String productDataType = getString(rs, "DATA_TYPE");
		String columnType = getString(rs, "COLUMN_TYPE");
		Long octetLength = getLong(rs, "CHARACTER_OCTET_LENGTH");
		Integer numericScale = getInteger(rs, "NUMERIC_SCALE");
		String extra = getString(rs, "EXTRA");
		// if ("set".equalsIgnoreCase(productDataType)
		// || "enum".equalsIgnoreCase(productDataType)) {
		productDataType = columnType;
		// }
		boolean nullable = "YES".equalsIgnoreCase(getString(rs, "IS_NULLABLE"));
		boolean autoIncrement = "auto_increment".equalsIgnoreCase(extra);
		long autoIncrementCurrent = rs.getLong("auto_increment");
		column.setNullable(nullable);
		column.setIdentity(autoIncrement);
		getDialect().setDbType(productDataType, getMaxLength(rs), numericScale, column);
		if (autoIncrement) {
			column.setIdentityLastValue(autoIncrementCurrent);
		}
		if (column.getDataType().isFixedSize()) {
			if (octetLength != null && octetLength.longValue() > 0) {
				column.setOctetLength(octetLength);
			}
		}
		Matcher matcher=ON_UPDATE_PATTERN.matcher(extra);
		if (matcher.matches()){
			column.setOnUpdate(matcher.group(1));
		}
		column.setRemarks(getString(rs, "COLUMN_COMMENT"));
		String def = getString(rs, "COLUMN_DEFAULT");
		setDefaultValue(connection, rs, column, def);
		column.setCharacterSet(getString(rs, CHARACTER_SET_NAME));
		column.setCollation(getString(rs, COLLATION_NAME));
		column.setCharacterSemantics(CharacterSemantics.Char);
		this.setStatistics(rs, "CARDINALITY", column);
		return column;
	}
	
	protected void setDefaultValue(final Connection connection, final ExResultSet rs, final Column column, final String def) {
		if (def != null) {
			DbDataType<?> dataType = this.getDialect().getDbDataType(column);
			column.setDefaultValue(dataType.withLiteral(def));
		}
	}
	
	protected Long getMaxLength(ExResultSet rs) throws SQLException{
		Long maxLength = getLong(rs, "CHARACTER_MAXIMUM_LENGTH");
		Long numericPrecision = getLong(rs, "NUMERIC_PRECISION");
		return CommonUtils.coalesce(maxLength, numericPrecision);
	}

	private static final Pattern ON_UPDATE_PATTERN = Pattern
			.compile("on\\s+update\\s+(.*?)\\s*", Pattern.CASE_INSENSITIVE);

	private static final Pattern FUNCTION_PATTERN = Pattern
			.compile("^[^0-9].*");

	protected boolean isFunction(final Connection connection, String value,
			final Map<String, Boolean> functionMap) {
		Boolean bool = functionMap.get(value);
		if (bool != null) {
			return bool.booleanValue();
		}
		Matcher matcher = FUNCTION_PATTERN.matcher(value);
		if (!matcher.matches()) {
			functionMap.put(value, Boolean.FALSE);
			return false;
		}
		try {
			String text = DbUtils.executeScalar(connection, "SELECT " + value,
					String.class);
			boolean ret = CommonUtils.eq(text, value);
			functionMap.put(value, ret);
			return ret;
		} catch (RuntimeException e) {
			functionMap.put(value, Boolean.FALSE);
			return false;
		}
	}
}
