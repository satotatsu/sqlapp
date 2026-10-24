/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Firebird 4.0 shifted the DDL object-class code range from 20 to 21. */
public class Firebird40UserPrivilegeReader extends Firebird30UserPrivilegeReader {
	protected Firebird40UserPrivilegeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getSqlSqlNode() {
		return getSqlNodeCache().getString("userPrivileges40.sql");
	}
}
