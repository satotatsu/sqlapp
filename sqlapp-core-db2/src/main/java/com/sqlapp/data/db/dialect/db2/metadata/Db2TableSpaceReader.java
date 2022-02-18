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

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TableSpaceFileReader;
import com.sqlapp.data.db.metadata.TableSpaceReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.TableSpace;

/**
 * DB2 table space Reader
 * 
 * @author satoh
 * 
 */
public class Db2TableSpaceReader extends TableSpaceReader {

	protected Db2TableSpaceReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<TableSpace> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<TableSpace> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				TableSpace tableSpace = createTableSpace(rs);
				result.add(tableSpace);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tableSpaces.sql");
	}

	protected TableSpace createTableSpace(ExResultSet rs) throws SQLException {
		String name = getString(rs, TABLESPACE_NAME);
		TableSpace tableSpace = new TableSpace(name);
		String ownerType=this.getString(rs, "OWNERTYPE");
		if ("U".equalsIgnoreCase(ownerType)){
			String owner=this.getString(rs, "OWNER");
			tableSpace.setOwnerName(owner);
		}
		tableSpace.setRemarks(this.getString(rs, "REMARKS"));
		tableSpace.setCreatedAt(this.getTimestamp(rs, "CREATE_TIME"));
		setSpecifics(rs, "EXTENTSIZE", tableSpace);
		setSpecifics(rs, "PREFETCHSIZE", tableSpace);
		setSpecifics(rs, "OVERHEAD", tableSpace);
		setSpecifics(rs, "TRANSFERRATE", tableSpace);
		setSpecifics(rs, "WRITEOVERHEAD", tableSpace);
		setSpecifics(rs, "WRITETRANSFERRATE", tableSpace);
		setSpecifics(rs, "PAGESIZE", tableSpace);
		return tableSpace;
	}

	@Override
	protected TableSpaceFileReader newTableSpaceFileReader() {
		return null;
	}
}
