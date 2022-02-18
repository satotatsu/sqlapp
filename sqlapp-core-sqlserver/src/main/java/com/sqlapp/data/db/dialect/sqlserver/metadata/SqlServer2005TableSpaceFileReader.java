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

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.TableSpaceFile;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * SqlServer2005のファイル読み込み
 * 
 * @author satoh
 * 
 */
public class SqlServer2005TableSpaceFileReader extends
		SqlServer2000TableSpaceFileReader {

	protected SqlServer2005TableSpaceFileReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tableSpaceFiles2005.sql");
	}

	@Override
	protected TableSpaceFile createStorageFile(ExResultSet rs)
			throws SQLException {
		TableSpaceFile obj = super.createStorageFile(rs);
		setStatistics(rs, "type_desc", "type", obj);
		setStatistics(rs, "state_desc", "state", obj);
		return obj;
	}

}
