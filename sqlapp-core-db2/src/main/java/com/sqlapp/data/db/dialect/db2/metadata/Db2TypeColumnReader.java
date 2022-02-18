/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.db2.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.trim;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TypeColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.TypeColumn;

/**
 * DB2のタイプカラム読み込みクラス
 * 
 * @author satoh
 * 
 */
public class Db2TypeColumnReader extends TypeColumnReader {

	protected Db2TypeColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<TypeColumn> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
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

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("typeColumns.sql");
	}

	protected TypeColumn createTypeColumn(ExResultSet rs) throws SQLException {
		TypeColumn obj = createObject(getString(rs, COLUMN_NAME));
		obj.setTypeName(TYPE_NAME);
		obj.setSchemaName(SCHEMA_NAME);
		Long length = this.getLong(rs, "LENGTH");
		Integer scale = this.getInteger(rs, "SCALE");
		String productDataType = getString(rs, DATA_TYPE);
		this.getDialect().setDbType(productDataType, length, scale, obj);
		String logged = trim(getString(rs, "LOGGED"));
		if (!isEmpty(logged)) {
			setSpecifics(rs, "LOGGED", obj);
		}
		return obj;
	}

}
