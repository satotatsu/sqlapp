/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.virtica.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.UserReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.User;

public class VirticaUserReader extends UserReader {

	protected VirticaUserReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<User> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<User> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				User obj = createUser(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("users.sql");
	}

	protected User createUser(ExResultSet rs) throws SQLException {
		User obj = new User(getString(rs, USER_NAME));
		obj.setAdmin(rs.getBoolean("IS_SUPER_USER"));
		obj.setLockedAt(getTimestamp(rs, "LOCK_TIME"));
		setSpecifics(rs, "PROFILE_NAME", obj);
		setSpecifics(rs, "IS_LOCKED", obj);
		setSpecifics(rs, "RESOURCE_POOL", obj);
		setSpecifics(rs, "MEMORY_CAP_KB", obj);
		setSpecifics(rs, "TEMP_SPACE_CAP_KB", obj);
		setSpecifics(rs, "RUN_TIME_CAP", obj);
		setSpecifics(rs, "ALL_ROLES", obj);
		setSpecifics(rs, "DEFAULT_ROLES", obj);
		setSpecifics(rs, "SEARCH_PATH", obj);
		return obj;
	}
}
