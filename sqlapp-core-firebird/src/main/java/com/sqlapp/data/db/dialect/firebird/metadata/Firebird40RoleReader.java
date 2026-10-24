/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSystemPrivileges;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Adds the system privilege bitmap introduced in Firebird 4.0, in the same bulk
 * query.
 */
public class Firebird40RoleReader extends Firebird20RoleReader {
	protected Firebird40RoleReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getSqlSqlNode(ProductVersionInfo version) {
		return getSqlNodeCache().getString("roles40.sql");
	}

	@Override
	protected Role createObject(ExResultSet rs) throws SQLException {
		Role role = super.createObject(rs);
		String privileges = FirebirdSystemPrivileges.decode(rs.getBytes("system_privileges"), getDialect());
		if (!privileges.isEmpty())
			role.getSpecifics().put(FirebirdSystemPrivileges.ROLE_PRIVILEGES, privileges);
		return role;
	}
}
