/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-firebird.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.firebird.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.PackageBodyReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.PackageBody;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

/**
 * Firebirdのパッケージ作成クラス
 * 
 * @author satoh
 * 
 */
public class Firebird30PackageBodyReader extends PackageBodyReader {

	protected Firebird30PackageBodyReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<PackageBody> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<PackageBody> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				PackageBody obj = createPackageBody(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected PackageBody createPackageBody(ExResultSet rs) throws SQLException {
		String name = CommonUtils.trim(getString(rs, "RDB$PACKAGE_NAME"));
		PackageBody obj = new PackageBody(name);
		String source = getString(rs, "RDB$PACKAGE_BODY_SOURCE");
		obj.setStatement(source);
		obj.setRemarks(CommonUtils.trim(getString(rs, "RDB$DESCRIPTION")));
		return obj;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("packageBodies.sql");
	}
}
