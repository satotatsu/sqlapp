/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.vertica.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;

/**
 * Virtica Column Reader
 * 
 * @author satoh
 * 
 */
public class VerticaColumnReader extends ColumnReader {

	public VerticaColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<Column> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Column obj = createColumn(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected Column createColumn(ExResultSet rs) throws SQLException {
		Column obj = new Column(getString(rs, COLUMN_NAME));
		boolean nullable = toBoolean(getString(rs, "IS_NULLABLE"));
		String data_type = getString(rs, "DATA_TYPE");
		boolean identity = toBoolean(getString(rs, "IS_IDENTITY"));
		Long char_maxlength = getLong(rs, "CHARACTER_MAXIMUM_LENGTH");
		Long numeric_precision = getLong(rs, "NUMERIC_PRECISION");
		Integer numeric_scale = getInteger(rs, "NUMERIC_SCALE");
		Integer datetime_precision = getInteger(rs, "DATETIME_PRECISION");
		obj.setNullable(nullable);
		obj.setIdentity(identity);
		if (identity) {
			obj.setSequenceName(getString(rs, "SEQUENCE_NAME"));
			obj.setIdentityStep(getLong(rs, "INCREMENT_BY"));
			Long cache = getLong(rs, "SESSION_CACHE_COUNT");
			if (cache != null && cache < 0)
				throw new IllegalStateException("Negative Vertica identity cache size: " + obj.getName());
			obj.setIdentityCacheSize(cache == null ? null : Math.toIntExact(cache));
			obj.setIdentityMinValue(getLong(rs, "MINIMUM"));
			obj.setIdentityMaxValue(getLong(rs, "MAXIMUM"));
			// Distributed allocation state is not the last value issued to a row.
			Long allocated = getLong(rs, "CURRENT_VALUE");
			if (allocated != null)
				obj.getSpecifics().put("CURRENT_VALUE", allocated);
			obj.setIdentityCycle(toBoolean(getString(rs, "ALLOW_CYCLE")));
		}
		var arrayBounds = new java.util.ArrayList<String>();
		while (data_type.toLowerCase(java.util.Locale.ROOT).startsWith("array[")
				&& data_type.toLowerCase(java.util.Locale.ROOT).contains("interval")) {
			int closing=data_type.lastIndexOf(']');
			if(closing<6) throw new IllegalArgumentException("Invalid Vertica ARRAY declaration");
			if(arrayBounds.isEmpty() && closing<data_type.length()-1) {
				String capacity=data_type.substring(closing+1);
				if(!capacity.matches("\\([1-9][0-9]*\\)")) throw new IllegalArgumentException("Invalid Vertica ARRAY capacity");
				obj.getSpecifics().put("vertica.interval.arrayMaxBytes",capacity.substring(1,capacity.length()-1));
			}
			String body = data_type.substring(6, closing).strip();
			int depth = 0, comma = -1;
			for(int i=0;i<body.length();i++) { char ch=body.charAt(i); if(ch=='[')depth++; else if(ch==']')depth--; else if(ch==','&&depth==0)comma=i; }
			arrayBounds.add(comma < 0 ? "*" : body.substring(comma+1).strip());
			data_type = (comma < 0 ? body : body.substring(0,comma)).strip();
		}
		if(!arrayBounds.isEmpty()) {
			data_type=data_type.toUpperCase(java.util.Locale.ROOT);
			if(data_type.matches("INTERVAL(?:\\([0-6]\\))?")) data_type=data_type.replace("INTERVAL","INTERVAL DAY TO SECOND");
		}
		this.getDialect().setDbType(data_type, maxNullable(char_maxlength, numeric_precision,
				datetime_precision == null ? null : datetime_precision.longValue()), numeric_scale, obj);
		// NUMERIC_SCALE is reported as zero even for temporal columns. It is not
		// their fractional-second precision; null DATETIME_PRECISION means default.
		if (obj.getDataType() != null && obj.getDataType().isInterval()) {
			obj.setLength(datetime_precision == null ? null : datetime_precision.longValue());
			obj.setScale((Integer) null);
			if (!arrayBounds.isEmpty()) {
				obj.setArrayDimension(arrayBounds.size());
				var precision = java.util.regex.Pattern.compile("\\(([0-6])\\)").matcher(data_type);
				if (precision.find()) obj.setLength(Long.parseLong(precision.group(1)));
				else obj.setLength(data_type.endsWith("SECOND") ? 6L : 0L);
				java.util.Collections.reverse(arrayBounds);
				obj.getSpecifics().put(com.sqlapp.data.db.datatype.IntervalArrays.DECLARED_BOUNDS, String.join(",",arrayBounds));
			}

		} else if (obj.getDataType() != null && obj.getDataType().isDateTime()) {
			obj.setLength(datetime_precision == null ? null : datetime_precision.longValue());
			obj.setScale(datetime_precision);
		}
		obj.setDefaultValue(getString(rs, "COLUMN_DEFAULT"));
		obj.setCatalogName(getString(rs, TABLE_CATALOG));
		obj.setSchemaName(getString(rs, TABLE_SCHEMA));
		obj.setTableName(getString(rs, TABLE_NAME));
		obj.setRemarks(getString(rs, "COMMENT"));
		return obj;
	}

	private Long maxNullable(final Long... values) {
		Long result = null;
		for (Long value : values) {
			if (value != null && (result == null || value > result)) {
				result = value;
			}
		}
		return result;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		if (productVersionInfo != null && productVersionInfo.gte(24, 1, null)) {
			return getSqlNodeCache().getString("columns24_1.sql");
		}
		return getSqlNodeCache().getString("columns.sql");
	}
}
