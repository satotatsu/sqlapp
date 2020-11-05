/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TableSpaceFileReader;
import com.sqlapp.data.db.metadata.TableSpaceReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.TableSpace;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Oracleのテーブルスペース読み込みクラス
 * 
 * @author satoh
 * 
 */
public class OracleTableSpaceReader extends TableSpaceReader {

	protected OracleTableSpaceReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<TableSpace> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		boolean dba = OracleMetadataUtils.hasSelectPrivilege(connection,
				this.getDialect(), "SYS", "DBA_TABLESPACES");
		final List<TableSpace> result = list();
		if (!dba){
			return result;
		}
		OracleMetadataUtils.setDbaOrUser(dba, context);
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
		setSpecifics(rs, "BLOCK_SIZE", tableSpace);
		setSpecifics(rs, "INITIAL_EXTENT", tableSpace);
		setSpecifics(rs, "NEXT_EXTENT", tableSpace);
		setSpecifics(rs, "MIN_EXTENTS", tableSpace);
		setSpecifics(rs, "MAX_EXTENTS", tableSpace);
		setSpecifics(rs, "PCT_INCREASE", tableSpace);
		setSpecifics(rs, "MIN_EXTLEN", tableSpace);
		setSpecifics(rs, "STATUS", tableSpace); // ONLINE OFFLINE READ ONLY
		setSpecifics(rs, "CONTENTS", tableSpace); // UNDO PERMANENT
														// TEMPORARY
		setSpecifics(rs, "LOGGING", tableSpace);
		setSpecifics(rs, "FORCE_LOGGING", tableSpace);
		setSpecifics(rs, "EXTENT_MANAGEMENT", tableSpace);
		setSpecifics(rs, "ALLOCATION_TYPE", tableSpace);
		setSpecifics(rs, "PLUGGED_IN", tableSpace);
		setSpecifics(rs, "SEGMENT_SPACE_MANAGEMENT", tableSpace);
		setSpecifics(rs, "DEF_TAB_COMPRESSION", tableSpace);
		setSpecifics(rs, "RETENTION", tableSpace);
		setSpecifics(rs, "BIGFILE", tableSpace);
		setSpecifics(rs, "GROUP_NAME", tableSpace);
		return tableSpace;
	}

	@Override
	protected TableSpaceFileReader newTableSpaceFileReader() {
		return new OracleTableSpaceFileReader(this.getDialect());
	}
}
