/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.notZero;
import static com.sqlapp.util.CommonUtils.trim;
import static com.sqlapp.util.CommonUtils.unwrap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TypeColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.TypeColumn;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * SqlServer2008のテーブル値型のカラム読み込み
 * 
 * @author satoh
 * 
 */
public class SqlServer2008TypeColumnReader extends TypeColumnReader {

	protected SqlServer2008TypeColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<TypeColumn> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<TypeColumn> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				TypeColumn column = createTypeColumn(rs);
				result.add(column);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("typeColumns2008.sql");
	}

	protected TypeColumn createTypeColumn(ExResultSet rs) throws SQLException {
		TypeColumn obj = createObject(getString(rs, COLUMN_NAME));
		String productDataType = getString(rs, "column_type_name");
		Long byteLength = getLong(rs, "max_length");
		Long max_length = SqlServerUtils.getMaxLength(productDataType,
				byteLength);
		long precision = rs.getLong("precision");
		Integer scale = getInteger(rs, "scale");
		obj.setNullable(rs.getBoolean("is_nullable"));
		obj.setIdentity(rs.getBoolean("is_identity"));
		this.getDialect().setDbType(productDataType,
				notZero(max_length, precision), scale, obj);
		obj.setDefaultValue(unwrap(getString(rs, "default_definition"), '(', ')'));
		// column.setOctetLength(rs.getInt("CHAR_OCTET_LENGTH"));
		obj.setCatalogName(getString(rs, CATALOG_NAME));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setTypeName(getString(rs, TYPE_NAME));
		if (obj.isIdentity()) {
			obj.setIdentityStartValue(rs.getLong("ident_seed"));
			obj.setIdentityStep(rs.getLong("ident_increment"));
			obj.setIdentityLastValue(rs.getLong("ident_current"));
		}
		obj.setRemarks(getString(rs, "remarks"));
		String check_definition = trim(unwrap(
				getString(rs, "check_definition"), '(', ')'));
		obj.setCheck(check_definition);
		setSpecifics(rs, "is_rowguidcol", obj);
		// setDbSpecificInfo(rs, "is_id_not_for_repl", column);
		setSpecifics(rs, "xmlschema", obj);
		return obj;
	}

}
