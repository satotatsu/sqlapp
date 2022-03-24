/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.oracle.metadata;

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
 * Oracleのテーブルスペースのファイル読み込みクラス
 * 
 * @author satoh
 * 
 */
public class OracleTableSpaceFileReader extends TableSpaceFileReader {

	protected OracleTableSpaceFileReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<TableSpaceFile> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		boolean dba = OracleMetadataUtils.hasSelectPrivilege(connection,
				this.getDialect(), "SYS", "DBA_DATA_FILES");
		final List<TableSpaceFile> result = list();
		if (!dba){
			return result;
		}
		OracleMetadataUtils.setDbaOrUser(dba, context);
		context.put("fileId", "");
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				TableSpaceFile storageFile = createStorageFile(rs);
				result.add(storageFile);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tableSpaceFiles.sql");
	}

	protected TableSpaceFile createStorageFile(ExResultSet rs)
			throws SQLException {
		TableSpaceFile obj = new TableSpaceFile(getString(rs, "FILE_NAME"),
				getString(rs, "FILE_NAME"));
		obj.setTableSpaceName(getString(rs, "TABLESPACE_NAME"));
		obj.setAutoExtensible("YES".equalsIgnoreCase(getString(rs,
				"AUTOEXTENSIBLE")));
		setSpecifics(rs, "FILE_ID", obj);
		setSpecifics(rs, "BYTES", obj);
		setSpecifics(rs, "BLOCKS", obj);
		setSpecifics(rs, "STATUS", obj);
		setSpecifics(rs, "MAXBYTES", obj);
		setSpecifics(rs, "MAXBLOCKS", obj);
		setSpecifics(rs, "INCREMENT_BY", obj);
		setSpecifics(rs, "USER_BYTES", obj);
		setSpecifics(rs, "USER_BLOCKS", obj);
		return obj;
	}
}
