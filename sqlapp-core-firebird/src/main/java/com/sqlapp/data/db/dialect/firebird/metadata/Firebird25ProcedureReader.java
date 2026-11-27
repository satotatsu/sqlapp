/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.jdbc.ExResultSet;

/**
 * Catalog validity is available on this dialect without touching pre-2.1
 * catalogs.
 */
public class Firebird25ProcedureReader extends FirebirdProcedureReader {
	protected Firebird25ProcedureReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Procedure createProcedure(ExResultSet row) throws SQLException {
		Procedure procedure = super.createProcedure(row);
		FirebirdCompilationState.blr(row, procedure);
		return procedure;
	}
}
