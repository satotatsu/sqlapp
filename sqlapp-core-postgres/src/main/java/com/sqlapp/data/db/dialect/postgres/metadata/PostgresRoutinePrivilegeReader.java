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
import static com.sqlapp.util.CommonUtils.split;
import static com.sqlapp.util.CommonUtils.unwrap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.RoutinePrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.RoutinePrivilege;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.SeparatedStringBuilder;

public class PostgresRoutinePrivilegeReader extends RoutinePrivilegeReader {

	protected PostgresRoutinePrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<RoutinePrivilege> doGetAll(final Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<RoutinePrivilege> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				RoutinePrivilege obj = createPrivilege(connection, rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("routinePrivileges.sql");
	}

	protected RoutinePrivilege createPrivilege(Connection connection,
			ExResultSet rs) throws SQLException {
		RoutinePrivilege obj = new RoutinePrivilege();
		obj.setCatalogName(getString(rs, CATALOG_NAME));
		obj.setSchemaName(getString(rs, "ROUTINE_SCHEMA"));
		obj.setObjectName(getString(rs, ROUTINE_NAME));
		obj.setPrivilege(getString(rs, PRIVILEGE_TYPE));
		obj.setGrantorName(getString(rs, GRANTOR));
		obj.setGranteeName(getString(rs, GRANTEE));
		obj.setGrantable("YES".equals(getString(rs, "IS_GRANTABLE")));
		obj.setHierachy("YES".equals(getString(rs, "WITH_HIERARCHY")));
		int argNo = rs.getInt("pronargs");
		Function function = new Function(getString(rs, ROUTINE_NAME));
		if (argNo > 0) {
			String allArgTypes = unwrap(rs.getString("proargtypes"), "{", "}");
			if (allArgTypes != null) {
				String[] argArray = split(allArgTypes, "[, ]");
				SeparatedStringBuilder builder = new SeparatedStringBuilder(",");
				List<NamedArgument> arguments = PostgresUtils.getTypeInfoById(
						connection, this.getDialect(), argArray);
				for (NamedArgument argument : arguments) {
					builder.add(argument.getDataTypeName());
				}
				obj.setSpecificName(function.getName() + "("
						+ builder.toString() + ")");
			}
		}
		return obj;
	}
}
