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

package com.sqlapp.data.db.dialect.standard.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.DbUtils.close;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.util.SqlBuilder;

/**
 * 汎用のカラム作成クラス
 * 
 * @author satoh
 * 
 */
public class ResultSetColumnReader extends ColumnReader {

	public ResultSetColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	public List<Column> getAllFull(Connection connection) {
		PreparedStatement ps = null;
		ExResultSet rs = null;
		List<Column> result = null;
		try {
			String sql = getMetadataSql(this.getCatalogName(),
					this.getSchemaName(), this.getObjectName());
			ps = connection.prepareStatement(sql);
			rs = new ExResultSet(ps.executeQuery());
			result = getColumnMetadata(rs, this.getColumnName());
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			close(rs);
			close(ps);
		}
		return result;
	}

	/**
	 * メタデータ取得用のSQLを取得します
	 * 
	 */
	private String getMetadataSql(String catalogName, String schemaName,
			String objectName) {
		SqlBuilder sql = new SqlBuilder(this.getDialect());
		sql.select().lineBreak(" *");
		sql.from()
				.space()
				._add(this.getDialect().getObjectFullName(catalogName,
						schemaName, objectName));
		sql.where()._add(" 1=0");
		return sql.toString();
	}

	/**
	 * ResultSetのメタデータのカラム情報の読み込み
	 * 
	 * @param rs
	 * @param table
	 *            データテーブル
	 * @throws SQLException
	 */
	protected List<Column> getColumnMetadata(ResultSet rs, String columnName)
			throws SQLException {
		Pattern pattern = null;
		if (!isEmpty(columnName)) {
			pattern = Pattern.compile(columnName.replaceAll("[%]", ".*"),
					Pattern.CASE_INSENSITIVE);
		}
		ResultSetMetaData metaData = rs.getMetaData();
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
			this.getDialect().setDbType(sqlType, productDataType, precision,
					scale, column);
			column.setNotNull(!allowDBNull);
			column.setIdentity(autoIncrement);
			if (pattern == null) {
				result.add(column);
			} else {
				if (pattern.matcher(colName).matches()) {
					result.add(column);
				}
			}
		}
		return result;
	}

	@Override
	protected List<Column> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		PreparedStatement ps = null;
		ExResultSet rs = null;
		List<Column> result = null;
		try {
			ps = connection.prepareStatement(getMetadataSql(
					this.getCatalogName(context), this.getSchemaName(context),
					this.getObjectName()));
			rs = new ExResultSet(ps.executeQuery());
			result = getColumnMetadata(rs, this.getColumnName());
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			close(rs);
			close(ps);
		}
		return result;
	}
}
