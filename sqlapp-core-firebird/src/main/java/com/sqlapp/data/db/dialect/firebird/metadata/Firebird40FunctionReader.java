/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.SqlSecurity;
import com.sqlapp.jdbc.ExResultSet;

/** Reads SQL SECURITY only on Firebird 4.0 and later. */
public class Firebird40FunctionReader extends Firebird30FunctionReader {
	protected Firebird40FunctionReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Function createFunction(ExResultSet rs) throws SQLException {
		Function routine = super.createFunction(rs);
		Object security = rs.getObject("RDB$SQL_SECURITY");
		if (security != null)
			routine.setSqlSecurity(rs.getBoolean("RDB$SQL_SECURITY") ? SqlSecurity.Definer : SqlSecurity.Invoker);
		return routine;
	}
}
