/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege;
import com.sqlapp.data.db.metadata.RoleReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Reads Firebird Role metadata in one catalog query. */
public class FirebirdRoleReader extends RoleReader {
	protected FirebirdRoleReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Role> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		List<Role> result = new ArrayList<>();
		execute(connection, getSqlSqlNode(version), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				result.add(createObject(rs));
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo version) {
		return getSqlNodeCache().getString("roles.sql");
	}

	protected Role createObject(ExResultSet rs) throws SQLException {
		Role obj = new Role(getString(rs, "role_name"));
		obj.setRemarks(getString(rs, "remarks"));
		obj.getSpecifics().put("firebird.owner", getString(rs, "owner_name"));
		return obj;
	}
}
