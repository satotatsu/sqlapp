/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;

/** SQL SECURITY is a relation catalog property from Firebird 4.0. */
public class Firebird40TableReader extends Firebird30TableReader {
	protected Firebird40TableReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Table createTable(ExResultSet rs) throws SQLException {
		Table table = super.createTable(rs);
		if (rs.getObject("RDB$SQL_SECURITY") != null)
			table.getSpecifics().put("firebird.sqlSecurity", rs.getBoolean("RDB$SQL_SECURITY") ? "DEFINER" : "INVOKER");
		return table;
	}
}
