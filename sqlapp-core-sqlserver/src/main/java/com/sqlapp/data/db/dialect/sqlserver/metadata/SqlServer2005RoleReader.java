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
import com.sqlapp.data.schemas.Role;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class SqlServer2005RoleReader extends SqlServer2000RoleReader {

	protected SqlServer2005RoleReader(Dialect dialect) {
		super(dialect);
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("roles2005.sql");
	}

	protected Role createRole(ExResultSet rs) throws SQLException {
		Role obj = super.createRole(rs);
		setSpecifics(rs, "type_desc", "type", obj);
		setSpecifics(rs, "owning_user_name", obj);
		setSpecifics(rs, "owning_principal_id", obj);
		return obj;
	}
}
