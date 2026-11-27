/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Package;
import com.sqlapp.jdbc.ExResultSet;

/** Package-level SQL SECURITY applies to all its member routines. */
public class Firebird40PackageReader extends Firebird30PackageReader {
	protected Firebird40PackageReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Package createPackage(ExResultSet rs) throws SQLException {
		Package obj = super.createPackage(rs);
		if (rs.getObject("RDB$SQL_SECURITY") != null)
			obj.getSpecifics().put("firebird.sqlSecurity", rs.getBoolean("RDB$SQL_SECURITY") ? "DEFINER" : "INVOKER");
		return obj;
	}
}
