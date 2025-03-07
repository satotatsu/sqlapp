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
import com.sqlapp.data.db.metadata.TableSpaceFileReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.TableSpaceFile;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * SqlServer2000のファイル読み込み
 * 
 * @author satoh
 * 
 */
public class SqlServer2000TableSpaceFileReader extends TableSpaceFileReader {

	protected SqlServer2000TableSpaceFileReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<TableSpaceFile> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<TableSpaceFile> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				TableSpaceFile obj = createStorageFile(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tableSpaceFiles2000.sql");
	}

	protected TableSpaceFile createStorageFile(ExResultSet rs)
			throws SQLException {
		TableSpaceFile obj = new TableSpaceFile(getString(rs, "name"),
				getString(rs, "physical_name"));
		obj.setCatalogName(getString(rs, CATALOG_NAME));
		obj.setTableSpaceName(getString(rs, "file_group_name"));
		obj.setAutoExtensible(rs.getInt("growth") > 0);
		setSpecifics(rs, "file_id", obj);
		setSpecifics(rs, "size", obj);
		setSpecifics(rs, "growth", obj);
		setSpecifics(rs, "max_size", obj);
		return obj;
	}

}
