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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.RoleReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Postgresのロール読み込み
 * 
 * @author satoh
 * 
 */
public class PostgresRoleReader extends RoleReader {

	protected PostgresRoleReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Role> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Role> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Role obj = createRole(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("roles.sql");
	}

	protected Role createRole(ExResultSet rs) throws SQLException {
		String name = getString(rs, "rolname");
		Role obj = new Role(name);
		obj.setId(getString(rs, "oid"));
		setSpecifics(rs, "rolsuper", "superuser", obj);
		setSpecifics(rs, "rolinherit", "inherit", obj);
		setSpecifics(rs, "rolcreaterole", "createrole", obj);
		setSpecifics(rs, "rolcreatedb", "createdb", obj);
		//TODO bugs
		//setDbSpecificInfo(rs, "rolcatupdate", "updatedb", obj);
		setSpecifics(rs, "rolcanlogin", "login", obj);
		setSpecifics(rs, "rolconnlimit", "connection limit", obj);
		// setDbSpecificInfo(rs, "rolpassword", obj);
		setSpecifics(rs, "rolvaliduntil", obj);
		setSpecifics(rs, "rolconfig", "config", obj);
		return obj;
	}
}
