/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.AssemblyFileReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.AssemblyFile;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * SqlServer2005のCLRアセンブリファイル読み込み
 * 
 * @author satoh
 * 
 */
public class SqlServer2005AssemblyFileReader extends AssemblyFileReader {

	protected SqlServer2005AssemblyFileReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<AssemblyFile> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<AssemblyFile> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				AssemblyFile obj = createAssemblyFile(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("assemblyFiles2005.sql");
	}

	protected AssemblyFile createAssemblyFile(ExResultSet rs) throws SQLException {
		AssemblyFile obj = new AssemblyFile(getString(rs, "file_name"));
		obj.setCatalogName(getString(rs, CATALOG_NAME));
		obj.setId("" + rs.getInt("file_id"));
		obj.setContent(rs.getBytes("content"));
		return obj;
	}

}
