/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.metadata;

import static com.sqlapp.util.CommonUtils.trim;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TableSpaceFileReader;
import com.sqlapp.data.db.metadata.TableSpaceReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.TableSpace;
import com.sqlapp.data.schemas.TableSpaceFile;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

/**
 * Firebirdのファイル読み込みクラス
 * 
 * @author satoh
 * 
 */
public class FirebirdTableSpaceReader extends TableSpaceReader {

	protected FirebirdTableSpaceReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<TableSpace> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<TableSpace> result = CommonUtils.list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String path = trim(getString(rs, "MON$DATABASE_NAME"));
				if (path == null || path.isBlank())
					throw new SQLException("Firebird database path or alias was not returned by the catalog");
				String[] splitPaths = path.split("[/\\\\]");
				String lastPath = splitPaths[splitPaths.length - 1];
				int extension = lastPath.lastIndexOf('.');
				String name = splitPaths.length > 1 && extension > 0 ? lastPath.substring(0, extension) : lastPath;
				TableSpace storageSpace = new TableSpace(name);
				storageSpace.setCreatedAt(rs.getTimestamp("MON$CREATION_DATE"));
				TableSpaceFile storageFile = new TableSpaceFile(path, path);
				storageSpace.getTableSpaceFiles().add(storageFile);
				result.add(storageSpace);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		boolean monitoring = getDialect()
				.compareTo(com.sqlapp.data.db.dialect.firebird.DialectHolder.defaultDialect25) >= 0;
		if (productVersionInfo != null && productVersionInfo.getMajorVersion() != null) {
			int major = productVersionInfo.getMajorVersion();
			Integer minor = productVersionInfo.getMinorVersion();
			monitoring = major > 2 || major == 2 && minor != null && minor >= 1;
		}
		return getSqlNodeCache().getString(monitoring ? "tableSpaces.sql" : "tableSpaces20.sql");
	}

	@Override
	protected TableSpaceFileReader newTableSpaceFileReader() {
		return null;
	}
}
