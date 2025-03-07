/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.splitLine;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TypeColumnReader;
import com.sqlapp.data.db.metadata.TypeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Postgresのタイプ読み込みクラス
 * 
 * @author satoh
 * 
 */
public class PostgresTypeReader extends TypeReader {

	protected PostgresTypeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Type> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Type> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Type obj = createType(rs);
				result.add(obj);
			}
		});
		return result;
	}

	@Override
	protected TypeColumnReader newColumnFactory() {
		return new PostgresTypeColumnReader(this.getDialect());
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("types.sql");
	}

	protected Type createType(ExResultSet rs) throws SQLException {
		Type obj = new Type(getString(rs, "type_name"));
		//obj.setSpecificName(getString(rs, "oid"));
		// obj.setCatalogName(getString(rs, "type_catalog"));
		obj.setSchemaName(getString(rs, "type_schema"));
		obj.setDefinition(splitLine(getString(rs, "definition")));
		obj.setRemarks(getString(rs, REMARKS));
		return obj;
	}

}
